package com.example.mobiledatacapturekit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.hardware.camera2.params.StreamConfigurationMap;
import java.util.Locale;
import java.util.Arrays;

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
            // 先在主线程获取尺寸
            int width = mTextureView.getWidth();
            int height = mTextureView.getHeight();
            // 【关键修改】将繁重的打开相机操作抛给后台线程，不阻塞 UI
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
            // 【关键修改】同样抛给后台线程
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
            // 简单逻辑：只选后置摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mCameraId = cameraId;
                    mCharacteristics = characteristics;
                    break;
                }
            }

            if (mCameraId == null) mCameraId = manager.getCameraIdList()[0]; // Fallback

            // 设置尺寸
            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
            mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), 1920, 1080); // 尝试 1080p

            // 初始化 ImageReader (用于拍照)
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(reader -> {
                // 保存图片
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

    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) return;

        try {
            closeSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);

            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);

            // 【关键修复】：这里必须把 mImageReader.getSurface() 也加进去，否则拍照会崩
            List<Surface> surfaces = Arrays.asList(surface, mImageReader.getSurface());

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (mCameraDevice == null) return;
                    mCaptureSession = session;
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
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
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 简单处理方向，实际项目需要根据设备旋转计算
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(mContext, "Saved to Gallery", Toast.LENGTH_SHORT).show();
                    startPreview(); // 重新开始预览
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

            // 准备元数据文件
            File dir = FileUtils.getDataDir(mContext, "Metadata");
            mMetadataFile = new File(dir, "META_" + FileUtils.getTimestamp() + ".csv");
            FileUtils.appendTextToFile(mMetadataFile, "Frame,Timestamp,Exposure,ISO,FocalLen\n");
            mFrameCount = 0;

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCaptureSession = session;
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                    try {
                        // 监听每一帧的元数据
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

        // 停止 Recorder 需要小心，防止过短录制崩溃
        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        } catch (RuntimeException e) {
            // 录制时间太短会抛出异常
            Log.e(TAG, "Video stop failed: " + e.getMessage());
        }

        if (mCurrentVideoFile != null && mCurrentVideoFile.exists()) {
            FileUtils.saveVideoToGallery(mContext, mCurrentVideoFile);
            Toast.makeText(mContext, "Video Saved: " + mCurrentVideoFile.getName(), Toast.LENGTH_LONG).show();
        }

        startPreview();
    }

    private void setUpMediaRecorder() throws IOException {
        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();
        else mMediaRecorder.reset();

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        // 暂存到私有目录，录制完成后再插入相册（避免 IO 冲突）
        File dir = FileUtils.getDataDir(mContext, "Videos");
        mCurrentVideoFile = new File(dir, "VID_" + FileUtils.getTimestamp() + ".mp4");

        mMediaRecorder.setOutputFile(mCurrentVideoFile.getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(90);
        mMediaRecorder.prepare();
    }

    private void processFrameMetadata(TotalCaptureResult result) {
        if (!isRecordingVideo || mMetadataFile == null) return;

        long timestamp = System.currentTimeMillis();
        long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ? result.get(CaptureResult.SENSOR_EXPOSURE_TIME) : 0;
        int iso = result.get(CaptureResult.SENSOR_SENSITIVITY) != null ? result.get(CaptureResult.SENSOR_SENSITIVITY) : 0;
        float focal = result.get(CaptureResult.LENS_FOCAL_LENGTH) != null ? result.get(CaptureResult.LENS_FOCAL_LENGTH) : 0;

        String line = String.format(Locale.getDefault(), "%d,%d,%d,%d,%.2f\n", mFrameCount++, timestamp, exposure, iso, focal);
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

    // 简单的选择合适尺寸的辅助方法
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
            return choices[0];
        }
    }
}