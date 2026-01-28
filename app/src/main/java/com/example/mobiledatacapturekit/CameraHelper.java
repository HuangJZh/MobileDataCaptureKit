package com.example.mobiledatacapturekit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraHelper {
    private static final String TAG = "CameraHelper";
    private final Context mContext;
    private final TextureView mTextureView;

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CameraCharacteristics mCharacteristics;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ImageReader mImageReader;
    private MediaRecorder mMediaRecorder;

    private Size mPreviewSize;
    private Size mVideoSize;

    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean isRecordingVideo = false;
    private File mCurrentVideoFile;
    private File mMetadataFile;
    private int mFrameCount = 0;

    public CameraHelper(Context context, TextureView textureView) {
        this.mContext = context;
        this.mTextureView = textureView;
    }

    public void onResume() {
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            int width = mTextureView.getWidth();
            int height = mTextureView.getHeight();
            if (mBackgroundHandler != null) {
                mBackgroundHandler.post(() -> openCamera(width, height));
            }
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (mBackgroundHandler != null) {
                mBackgroundHandler.post(() -> openCamera(width, height));
            }
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mCameraId = cameraId;
                    mCharacteristics = characteristics;
                    break;
                }
            }
            if (mCameraId == null) mCameraId = manager.getCameraIdList()[0];

            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // 使用 AppConfig 的分辨率设置
            mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class),
                    AppConfig.VIDEO_WIDTH, AppConfig.VIDEO_HEIGHT);
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);

            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(reader -> {
                FileUtils.saveImageToGallery(mContext, reader.acquireNextImage());
            }, mBackgroundHandler);

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

        } catch (CameraAccessException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            startPreview();
        }
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    /**
     * 【关键】统一应用 SLAM/Config 参数
     */
    private void applyConfigSettings(CaptureRequest.Builder builder) {
        // 1. 对焦 (Focus)
        if (AppConfig.ENABLE_AUTO_FOCUS) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, AppConfig.FIXED_FOCUS_DISTANCE);
        }

        // 2. 曝光 (Exposure)
        if (AppConfig.ENABLE_AUTO_EXPOSURE) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, AppConfig.FIXED_ISO);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, AppConfig.FIXED_EXPOSURE_TIME_NS);
            long frameDuration = 1_000_000_000L / AppConfig.VIDEO_FPS;
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
        }

        // 3. 【SLAM必须】关闭光学防抖 (OIS) - 防止内参(Intrinsics)变化
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        // 4. 【SLAM必须】关闭电子防抖 (EIS) - 防止图像裁剪和扭曲
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

        // 5. 【SLAM建议】关闭/降低 ISP 后处理 (保留纹理细节)
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST);
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST);
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST);
    }

    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) return;
        try {
            closeSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);

            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.addTarget(mImageReader.getSurface());

            applyConfigSettings(builder); // 应用配置

            List<Surface> surfaces = Arrays.asList(surface, mImageReader.getSurface());
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (mCameraDevice == null) return;
                    mCaptureSession = session;
                    try {
                        mCaptureSession.setRepeatingRequest(builder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {}
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() {
        if (mCameraDevice == null) return;
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            applyConfigSettings(captureBuilder); // 保持曝光一致
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(mContext, "Saved to Gallery", Toast.LENGTH_SHORT).show();
                    startPreview();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startRecordingVideo() {
        if (mCameraDevice == null || !mTextureView.isAvailable()) return;
        try {
            closeSession();
            setUpMediaRecorder();

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = mMediaRecorder.getSurface();

            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(previewSurface);
            builder.addTarget(recorderSurface);

            applyConfigSettings(builder); // 应用配置

            File dir = FileUtils.getDataDir(mContext, "Metadata");
            mMetadataFile = new File(dir, "META_" + FileUtils.getTimestamp() + ".csv");
            // 修正 Header，明确时间戳单位
            FileUtils.appendTextToFile(mMetadataFile, "Frame,Timestamp(ns),Exposure(ns),ISO,FocalLen\n");
            mFrameCount = 0;

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCaptureSession = session;
                    try {
                        mCaptureSession.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                processFrameMetadata(result);
                            }
                        }, mBackgroundHandler);
                        mMediaRecorder.start();
                        isRecordingVideo = true;
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {}
            }, mBackgroundHandler);

        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void stopRecordingVideo() {
        if (!isRecordingVideo) return;
        isRecordingVideo = false;
        try {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        } catch (RuntimeException e) {
            Log.e(TAG, "Video stop failed: " + e.getMessage());
        }

        if (mCurrentVideoFile != null && mCurrentVideoFile.exists()) {
            FileUtils.saveVideoToGallery(mContext, mCurrentVideoFile);
            Toast.makeText(mContext, "Video Saved", Toast.LENGTH_LONG).show();
        }
        startPreview();
    }

    private void setUpMediaRecorder() throws IOException {
        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();
        else mMediaRecorder.reset();

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        File dir = FileUtils.getDataDir(mContext, "Videos");
        mCurrentVideoFile = new File(dir, "VID_" + FileUtils.getTimestamp() + ".mp4");
        mMediaRecorder.setOutputFile(mCurrentVideoFile.getAbsolutePath());

        // 使用 AppConfig 参数
        mMediaRecorder.setVideoEncodingBitRate(AppConfig.VIDEO_BITRATE);
        mMediaRecorder.setVideoFrameRate(AppConfig.VIDEO_FPS);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(90);
        mMediaRecorder.prepare();
    }

    private void processFrameMetadata(TotalCaptureResult result) {
        if (!isRecordingVideo || mMetadataFile == null) return;

        // 【SLAM关键】获取硬件生成时间戳，与 IMU 时间戳对其
        Long sensorTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        long ts = (sensorTimestamp != null) ? sensorTimestamp : System.nanoTime();

        long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ? result.get(CaptureResult.SENSOR_EXPOSURE_TIME) : 0;
        int iso = result.get(CaptureResult.SENSOR_SENSITIVITY) != null ? result.get(CaptureResult.SENSOR_SENSITIVITY) : 0;
        float focal = result.get(CaptureResult.LENS_FOCAL_LENGTH) != null ? result.get(CaptureResult.LENS_FOCAL_LENGTH) : 0;

        String line = String.format(Locale.getDefault(), "%d,%d,%d,%d,%.2f\n", mFrameCount++, ts, exposure, iso, focal);
        FileUtils.appendTextToFile(mMetadataFile, line);
    }

    private void closeSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closeSession();
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null) return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
                }
            });
        } else {
            if (choices.length > 0) return choices[0];
            return new Size(width, height);
        }
    }
}