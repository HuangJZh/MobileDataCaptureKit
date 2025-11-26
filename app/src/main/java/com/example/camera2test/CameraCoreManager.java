package com.example.camera2test;

import static android.hardware.camera2.CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;


import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Choreographer;
import android.view.Surface;
import android.view.TextureView;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class CameraCoreManager {
    private static final String TAG = "CameraDemo";

    private Context mContext;
    private CameraManager mCameraManager;
    private String mCameraId;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCameraCharacteristics;
    private MediaRecorder mMediaRecorder;

    //Max preview width&height that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_WIDTH = 1080;
    private static final int MAX_PREVIEW_HEIGHT = 720;

    //A Semaphore to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private Size mPreviewSize = new Size(1280, 720);
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest.Builder mRecordRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private int mFacing = CameraCharacteristics.LENS_FACING_BACK;
    private Choreographer.FrameCallback mFrameCallback;
    private SurfaceTexture mSurfaceTexture;
    private File mCameraFile;
    private File mVideoFile;
    private TextureView mTextureView;
    private File mMetadataFile;

    private enum State{
        STATE_PREVIEW,
        STATE_CAPTURE,
    }
    State mState = State.STATE_PREVIEW;
    private int frameCount = 0;


    //camera capture process
    private ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if(mState == State.STATE_PREVIEW){
                //Log.d(TAG, "##### onFrame: Preview");
                Image image = reader.acquireNextImage();
                image.close();
            }else if(mState == State.STATE_CAPTURE) {
                Log.d(TAG,"capture one picture to gallery");
                mCameraFile = new File("aa_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg");
                mCameraHandler.post(new FileUtils.ImageSaver(mContext, reader.acquireLatestImage(), mCameraFile));
                mState = State.STATE_PREVIEW;
            }else{
                Log.d(TAG, "##### onFrame: default/nothing");
            }
        }
    };

    //camera preview process，step2 mStateCallback
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //重写onOpened方法
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e("DEBUG", "onError: " + error);
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
            Log.e("DEBUG", "onError:  restart camera");
            stopPreview();
            startPreview();
        }
    };

    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    public CameraCoreManager(Context context, TextureView textureView) {
        mContext = context;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mMediaRecorder = new MediaRecorder();
        mState = State.STATE_PREVIEW;
        mTextureView = textureView;
    }

    public void startPreview() {
        Log.d(TAG,"startPreview");
        if (!chooseCameraIdByFacing()) {
            Log.e(TAG, "Choose camera failed.");
            return;
        }

        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mImageAvailableListener, mCameraHandler);
        }else{
            mImageReader.close();
        }
        openCamera();
    }

    public void stopPreview() {
        Log.d(TAG,"stopPreview");
        closeCamera();
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            mCameraThread = null;
        }
        mCameraHandler = null;
    }

    private boolean chooseCameraIdByFacing() {
        try {
            String ids[] = mCameraManager.getCameraIdList();
            if (ids.length == 0) {
                Log.e(TAG, "No available camera.");
                return false;
            }

            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue;
                }

                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    continue;
                }
                if (internal == mFacing) {
                    mCameraId = cameraId;
                    mCameraCharacteristics = characteristics;
                    return true;
                }
            }

            mCameraId = ids[1];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer level = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false;
            }

            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                return false;
            }
            mFacing = CameraCharacteristics.LENS_FACING_BACK;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public void openCamera() {
        if (TextUtils.isEmpty(mCameraId)) {
            Log.e(TAG, "Open camera failed. No camera available");
            return;
        }

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            //camera preview process，step1 打开camera
            mCameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (InterruptedException | CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }
    private void startCaptureSession() {
        mState = State.STATE_PREVIEW;
        if (mCameraDevice == null) {
            return;
        }

        if ((mImageReader != null || mSurfaceTexture != null)) {
            try {
                closeCameraCaptureSession();
                //camera preview process，step3 创建一个 CaptureRequest.Builder，templateType来区分是拍照还是预览
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //camera preview process，step4 将显示预览用的surface的实例传入，即将显示预览用的 surface 的实例，作为一个显示层添加到该 请求的目标列表中
                mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
                List<Surface> surfaceList = Arrays.asList(mImageReader.getSurface());
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    Surface surface = new Surface(mSurfaceTexture);
                    mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
                    mPreviewRequestBuilder.addTarget(surface);
                    //camera preview process，step5 将显示预览用的surface的实例传入，即将显示预览用的surface的实例，作为一个显示层添加到该请求的目标列表中
                    surfaceList = Arrays.asList(surface, mImageReader.getSurface());
                }

                Range<Integer>[] fpsRanges = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Log.d("DEBUG", "##### fpsRange: " + Arrays.toString(fpsRanges));
                //camera preview process，step6 & 7
                // 6 执行createCaptureSession方法
                // 7 参数中实例化 CameraCaptureSession.stateCallback，并重写 onConfigured 方法
                mCameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        if (mCameraDevice == null) return;
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                        mCaptureSession = session;
                        try {
                            if (mCaptureSession != null)
                                //camera preview process，step8 用 CameraCaptureSession.setRepeatingRequest()方法创建预览
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
                        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException | NullPointerException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        Log.e(TAG, "Failed to configure capture session");
                    }

                    @Override
                    public void onClosed(CameraCaptureSession session) {
                        if (mCaptureSession != null && mCaptureSession.equals(session)) {
                            mCaptureSession = null;
                        }
                    }
                }, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            } catch (IllegalStateException e) {
                stopPreview();
                startPreview();
            } catch (UnsupportedOperationException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public void captureStillPicture() {
        try {
            Log.d(TAG,"captureStillPicture");
            mState = State.STATE_CAPTURE;
            if (mCameraDevice == null) {
                return;
            }
            // camera capture process，step1 创建作为拍照的CaptureRequest.Builder
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // camera capture process，step2 将imageReader的surface作为CaptureRequest.Builder的目标
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            // 设置自动对焦模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 设置自动曝光模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 设置为自动模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // 设置摄像头旋转角度
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, Surface.ROTATION_0);
            // 停止连续取景
            mCaptureSession.stopRepeating();
            // camera capture process，step5 &6 捕获静态图像，结束后执行onCaptureCompleted
            mCaptureSession.capture(mCaptureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override// 拍照完成时激发该方法
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Log.d(TAG,"onCaptureCompleted");
                    startCaptureSession();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder(){
        //camera record process，step1 停止预览，准备切换到录制视频
        try {
            mCaptureSession.stopRepeating();
            closeCameraCaptureSession();
            // 创建CaptureRequest.Builder对象，并用CameraDevice.TEMPLATE_RECORD参数初始化
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // 禁用光学图像稳定化（OIS）和数字图像稳定化（DIS）
            mPreviewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        long netTimeTimestampDT = System.currentTimeMillis();//ms
        long timestampDT = SystemClock.elapsedRealtimeNanos();//ns
        long timestampInMilliseconds = timestampDT / 1_000_000; // 将纳秒转换为毫秒
        long dt=netTimeTimestampDT-timestampInMilliseconds;
        frameCount = 1;
        //camera record process，step2 mMediaRecorder相关设置
        mVideoFile = new File(mContext.getExternalCacheDir(),new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) +"demo.mp4");
        mMetadataFile = new File(mContext.getExternalCacheDir(), new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) +"metadata.txt");
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//设置音频来源
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//设置视频来源
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//设置输出格式
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置音频编码格式AAC
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//设置视频编码H264
        mMediaRecorder.setVideoEncodingBitRate(4 * 1024 * 1024);//设置比特率
        mMediaRecorder.setVideoFrameRate(30);//设置帧数
        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        mMediaRecorder.setOrientationHint(90);
        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());


        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        Surface recorderSurface = mMediaRecorder.getSurface();//从获取录制视频需要的Surface


        try {
            //camera record process，step3 创建CaptureRequest.Build 对象，并用CameraDevice.TEMPLATE_RECORED 参数初始化
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //camera record process，step4 将MediaRecorder和预览用的Surface实例添加到该请求的目标列表中
            mPreviewRequestBuilder.addTarget(previewSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);
            //设置Arrays.asList(previewSurface,recorderSurface) 2个Surface，第一个是预览的Surface，第二个是录制视频使用的Surface
            //camera record process，step5 执行CameraDevice.CreateCaptureSession 方法
            String cameraId =mCameraManager.getCameraIdList()[0]; // 选择摄像头
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,recorderSurface),new CameraCaptureSession.StateCallback() {// 在设置捕获请求之前，创建CaptureRequest.Builder

                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    Log.i(TAG, "Video Session:" + mCaptureSession.toString());
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

                    // Add a callback for frame metadata
                    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            long timestampfromcapture = result.get(CaptureResult.SENSOR_TIMESTAMP);//ns
                            long netTimeTimestamp = System.currentTimeMillis();//ms
                            long timestampInMilliseconds = timestampfromcapture / 1_000_000; // 将纳秒转换为毫秒
                            long timestampfromcapturecorrection=timestampInMilliseconds +dt;
                            Date df1 = new java.util.Date(timestampfromcapturecorrection);
                            String vv1 = new SimpleDateFormat("hh:mm:ss:SSS").format(df1);
                            Date df2 = new java.util.Date(netTimeTimestamp );
                            String vv2 = new SimpleDateFormat("hh:mm:ss:SSS").format(df2);
                            // Extract frame metadata
                            long frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION); // 帧持续时间（纳秒）
                            int iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                            long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                            float focalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH);
                            float aperture = result.get(CaptureResult.LENS_APERTURE);

                            float focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE); // 焦距距离（屈光度）
                            float fx = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION))[0];
                            float fy = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION))[1];
                            float cx = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION))[2];
                            float cy = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION))[3];

                            // Format metadata as a string
                            String metadata = String.format(Locale.getDefault(),
                                    "%d,%s,%s,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                                    frameCount,//%d
                                    vv1,//%s
                                    vv2,//%s
                                    frameDuration,//%d
                                    iso,//%d
                                    exposureTime,//%d
                                    focalLength,
                                    aperture,
                                    focusDistanceDiopters,
                                    fx,
                                    fy,
                                    cx,
                                    cy//%.2f
                            );


                            // Write metadata to the text file
                            writeMetadataToFile(metadata);

                            frameCount++; // Increment frame count
                        }
                    };

                    try {
                        // Set the capture callback for frame metadata
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), captureCallback, mCameraHandler);

                        mMediaRecorder.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                }
            },mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


        // 开始将元数据写入文本文件
        writeMetadataToFile("帧,时间,帧持续时间（ns）,ISO,曝光时间,焦距,光圈,焦距距离（屈光度）,fx,fy,cx,cy\n");

    }
    private void writeMetadataToFile(String metadata) {
        try {
            FileWriter writer = new FileWriter(mMetadataFile, true); // 追加模式
            writer.append(metadata);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void closeCameraCaptureSession() {
        if (mCaptureSession != null) {
            Log.i(TAG, "close Session:" + mCaptureSession.toString());
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }
    public void startRecorder(){
        Log.d(TAG,"startRecorder");
        setupMediaRecorder();
    }

    public void stopRecorder(){
        Log.d(TAG,"stopRecorder");
        try {
            //camera record process，step6 取消持续捕获
            mCaptureSession.stopRepeating();
            //丢弃当前待处理和正在进行的所有捕获
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //camera record process，step7 停止图像捕获 并且重启预览模式
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        //可根据需要将视频设置为系统gallery可见
        mCameraHandler.post(new FileUtils.VideoSaver(mContext, mVideoFile));
        startCaptureSession();
    }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        mSurfaceTexture = surfaceTexture;
    }
    static class FileUtils {
        private static final String TAG = "FileUtils";

        //camera capture progress
        public static class ImageSaver implements Runnable {
            private final Image mImage;
            private final File mFile;
            Context mContext;

            ImageSaver(Context context,Image image, File file) {
                mContext = context;
                mImage = image;
                mFile = file;
            }

            @Override
            public void run() {
                Log.d(TAG,"take picture Image Run");
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DESCRIPTION, "This is an qr image");
                values.put(MediaStore.Images.Media.DISPLAY_NAME, mFile.getName());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.TITLE, "Image.jpg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/");
                Uri external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver resolver = mContext.getContentResolver();
                Uri insertUri = resolver.insert(external, values);
                OutputStream os = null;
                try {
                    if (insertUri != null) {
                        os = resolver.openOutputStream(insertUri);
                    }
                    if (os != null) {
                        os.write(bytes);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mImage.close();
                    try {
                        if(os!=null) {
                            os.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public static class VideoSaver implements Runnable {
            private final File mFile;
            Context mContext;

            VideoSaver(Context context,File file) {
                mContext = context;
                mFile = file;
            }

            private ContentValues getVideoContentValues(File paramFile,long paramLong) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.TITLE, paramFile.getName());
                values.put(MediaStore.Video.Media.DISPLAY_NAME, paramFile.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.DATE_TAKEN, Long.valueOf(paramLong));
                values.put(MediaStore.Video.Media.DATE_MODIFIED, Long.valueOf(paramLong));
                values.put(MediaStore.Video.Media.DATE_ADDED, Long.valueOf(paramLong));
                values.put(MediaStore.Video.Media.DATA, paramFile.getAbsolutePath());
                values.put(MediaStore.Video.Media.SIZE, Long.valueOf(paramFile.length()));
                return values;
            }

            @Override
            public void run() {
                Log.d(TAG, "recorder video Run");
                ContentResolver localContentResolver = mContext.getContentResolver();
                ContentValues localContentValues = getVideoContentValues(mFile, System.currentTimeMillis());
                Uri localUri = localContentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, localContentValues);
                OutputStream os = null;
                FileInputStream fis = null;
                byte[] buf = new byte[1024];
                int len;
                try {
                    if (localUri != null) {
                        fis = new FileInputStream(mFile);
                        os = localContentResolver.openOutputStream(localUri);
                    }
                    if (os != null) {
                        while ((len = fis.read(buf)) >= 0) {
                            os.write(buf, 0, len);
                        }
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if(os!=null) {
                            os.close();
                        }
                        if(fis!=null){
                            fis.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}