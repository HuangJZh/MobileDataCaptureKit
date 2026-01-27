package com.example.mobiledatacapturekit; // 确保包名正确

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;
    // Android 10+ 不需要存储权限，只需相机和录音
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private TextureView mTextureView;
    private CameraHelper mCameraHelper;
    private SensorHelper mSensorHelper;

    // UI Elements
    private TextView tvAcc, tvGyro, tvPressure, tvMag;
    private Button btnSensorLog; // 控制传感器
    private Button btnVideoRecord; // 控制录像

    // 独立的状态标志
    private boolean isSensorLogging = false;
    private boolean isVideoRecording = false;

    private long lastUiUpdateTime = 0;
    private static final long UI_UPDATE_INTERVAL_MS = 100; // 100ms更新一次(10Hz)，足够人眼看了

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // --- 1. 绑定视图 ---
        mTextureView = findViewById(R.id.texture_view);
        tvAcc = findViewById(R.id.accelerometerTextView);
        tvGyro = findViewById(R.id.gyroscopeTextView);
        tvPressure = findViewById(R.id.pressureTextView);
        tvMag = findViewById(R.id.magneticFieldTextView);

        ImageButton btnCapture = findViewById(R.id.camera_take_picture);

        // 对应 XML 中的 "videostartstop" (用于控制传感器数据 Log)
        btnSensorLog = findViewById(R.id.videostartstop);

        // 对应 XML 中的 "startStopButton" (用于控制视频录制 Record)
        btnVideoRecord = findViewById(R.id.startStopButton);

        // 初始化按钮文字状态
        updateButtonUI();

        // --- 2. 初始化 Helpers ---
        mCameraHelper = new CameraHelper(this, mTextureView);

        // 【核心优化】初始化 SensorHelper 并添加节流逻辑
        // 只有当时间间隔超过 100ms 时才刷新 TextView，解决启动卡顿和 UI 掉帧问题
        mSensorHelper = new SensorHelper(this, (sensorName, dataText) -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUiUpdateTime > UI_UPDATE_INTERVAL_MS) {
                lastUiUpdateTime = currentTime;

                runOnUiThread(() -> {
                    switch (sensorName) {
                        case "Accelerometer":
                            tvAcc.setText("ACC: " + dataText);
                            break;
                        case "Gyroscope":
                            tvGyro.setText("GYRO: " + dataText);
                            break;
                        case "Pressure":
                            tvPressure.setText("PRESS: " + dataText);
                            break;
                        case "Magnetic":
                            tvMag.setText("MAG: " + dataText);
                            break;
                    }
                });
            }
        });

        // --- 3. 设置点击监听器 ---

        // A. 拍照按钮
        btnCapture.setOnClickListener(v -> mCameraHelper.takePicture());

        // B. 传感器数据记录按钮 (LOG DATA)
        btnSensorLog.setOnClickListener(v -> {
            if (isSensorLogging) {
                // 停止记录
                mSensorHelper.stopRecording();
                isSensorLogging = false;
                Toast.makeText(this, "Sensor Log Saved", Toast.LENGTH_SHORT).show();
            } else {
                // 开始记录
                File sensorFile = new File(FileUtils.getDataDir(this, "SensorData"), "SENS_" + FileUtils.getTimestamp() + ".csv");
                mSensorHelper.startRecording(sensorFile);
                isSensorLogging = true;
                Toast.makeText(this, "Sensor Log Started", Toast.LENGTH_SHORT).show();
            }
            updateButtonUI();
        });

        // C. 视频录制按钮 (RECORD VIDEO)
        btnVideoRecord.setOnClickListener(v -> {
            if (isVideoRecording) {
                // 停止录像
                mCameraHelper.stopRecordingVideo();
                isVideoRecording = false;
            } else {
                // 开始录像
                mCameraHelper.startRecordingVideo();
                isVideoRecording = true;
            }
            updateButtonUI();
        });

        // --- 4. 检查权限 ---
        checkPermissions();
    }

    private void updateButtonUI() {
        // 更新传感器按钮文本
        if (isSensorLogging) {
            btnSensorLog.setText("Stop Data");
        } else {
            btnSensorLog.setText("Start Data");
        }

        // 更新录像按钮文本
        if (isVideoRecording) {
            btnVideoRecord.setText("Stop Video");
        } else {
            btnVideoRecord.setText("Start Video");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions()) {
            mCameraHelper.onResume();
            mSensorHelper.startSensors();
        }
    }

    @Override
    protected void onPause() {
        mCameraHelper.onPause();
        mSensorHelper.stopSensors();
        // 如果退出应用时还在录制，强制停止以保存文件
        if (isSensorLogging) {
            mSensorHelper.stopRecording();
            isSensorLogging = false;
        }
        if (isVideoRecording) {
            mCameraHelper.stopRecordingVideo();
            isVideoRecording = false;
        }
        super.onPause();
    }

    // --- Permissions Logic ---
    private void checkPermissions() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissions() {
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasPermissions()) {
                mCameraHelper.onResume();
                mSensorHelper.startSensors();
            } else {
                Toast.makeText(this, "Permissions needed", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}