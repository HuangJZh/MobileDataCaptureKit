package com.example.mobiledatacapturekit;

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
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private TextureView mTextureView;
    private CameraHelper mCameraHelper;
    private SensorHelper mSensorHelper;

    private TextView tvAcc, tvGyro, tvPressure, tvMag;
    private Button btnSensorLog;
    private Button btnVideoRecord;

    private boolean isSensorLogging = false;
    private boolean isVideoRecording = false;

    private long lastUiUpdateTime = 0;
    private static final long UI_UPDATE_INTERVAL_MS = 100; // 10Hz UI刷新率

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mTextureView = findViewById(R.id.texture_view);
        tvAcc = findViewById(R.id.accelerometerTextView);
        tvGyro = findViewById(R.id.gyroscopeTextView);
        tvPressure = findViewById(R.id.pressureTextView);
        tvMag = findViewById(R.id.magneticFieldTextView);

        ImageButton btnCapture = findViewById(R.id.camera_take_picture);
        btnSensorLog = findViewById(R.id.videostartstop);
        btnVideoRecord = findViewById(R.id.startStopButton);

        updateButtonUI();

        mCameraHelper = new CameraHelper(this, mTextureView);

        // 初始化 SensorHelper 并包含 UI 节流逻辑
        mSensorHelper = new SensorHelper(this, (sensorName, dataText) -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUiUpdateTime > UI_UPDATE_INTERVAL_MS) {
                lastUiUpdateTime = currentTime;
                runOnUiThread(() -> {
                    switch (sensorName) {
                        case "Accelerometer": tvAcc.setText("ACC: " + dataText); break;
                        case "Gyroscope": tvGyro.setText("GYRO: " + dataText); break;
                        case "Pressure": tvPressure.setText("PRESS: " + dataText); break;
                        case "Magnetic": tvMag.setText("MAG: " + dataText); break;
                    }
                });
            }
        });

        btnCapture.setOnClickListener(v -> mCameraHelper.takePicture());

        btnSensorLog.setOnClickListener(v -> {
            if (isSensorLogging) {
                mSensorHelper.stopRecording();
                isSensorLogging = false;
                Toast.makeText(this, "Sensor Log Saved", Toast.LENGTH_SHORT).show();
            } else {
                File sensorFile = new File(FileUtils.getDataDir(this, "SensorData"), "SENS_" + FileUtils.getTimestamp() + ".csv");
                mSensorHelper.startRecording(sensorFile);
                isSensorLogging = true;
                Toast.makeText(this, "Sensor Log Started", Toast.LENGTH_SHORT).show();
            }
            updateButtonUI();
        });

        btnVideoRecord.setOnClickListener(v -> {
            if (isVideoRecording) {
                mCameraHelper.stopRecordingVideo();
                isVideoRecording = false;
            } else {
                mCameraHelper.startRecordingVideo();
                isVideoRecording = true;
            }
            updateButtonUI();
        });

        checkPermissions();
    }

    private void updateButtonUI() {
        if (isSensorLogging) btnSensorLog.setText("Stop Data");
        else btnSensorLog.setText("Start Data");

        if (isVideoRecording) btnVideoRecord.setText("Stop Video");
        else btnVideoRecord.setText("Start Video");
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