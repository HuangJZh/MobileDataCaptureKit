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

    // UI Elements
    private TextView tvAcc, tvGyro, tvPressure, tvMag;
    private Button btnRecord;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Bind Views
        mTextureView = findViewById(R.id.texture_view);
        tvAcc = findViewById(R.id.accelerometerTextView);
        tvGyro = findViewById(R.id.gyroscopeTextView);
        tvPressure = findViewById(R.id.pressureTextView);
        tvMag = findViewById(R.id.magneticFieldTextView);

        ImageButton btnCapture = findViewById(R.id.camera_take_picture);
        btnRecord = findViewById(R.id.videostartstop);
        Button btnSensorRecord = findViewById(R.id.startStopButton); // 重用布局中的按钮

        // Init Helpers
        mCameraHelper = new CameraHelper(this, mTextureView);
        mSensorHelper = new SensorHelper(this, (sensorName, dataText) -> {
            // 安全地在 UI 线程更新
            runOnUiThread(() -> {
                switch (sensorName) {
                    case "Accelerometer": tvAcc.setText("ACC:\n" + dataText); break;
                    case "Gyroscope": tvGyro.setText("GYRO:\n" + dataText); break;
                    case "Pressure": tvPressure.setText("PRESS:\n" + dataText); break;
                    case "Magnetic": tvMag.setText("MAG:\n" + dataText); break;
                }
            });
        });

        // Click Listeners
        btnCapture.setOnClickListener(v -> mCameraHelper.takePicture());

        btnRecord.setOnClickListener(v -> toggleVideoRecording());

        btnSensorRecord.setOnClickListener(v -> {
            // 这里为了演示，可以把 Sensor 记录和 Video 记录解耦，或者合二为一
            // 当前逻辑：点击此按钮仅记录传感器数据
            Toast.makeText(this, "Sensor logging logic separate from video in this refactor", Toast.LENGTH_SHORT).show();
        });

        checkPermissions();
    }

    private void toggleVideoRecording() {
        if (isRecording) {
            // Stop
            mCameraHelper.stopRecordingVideo();
            mSensorHelper.stopRecording();
            btnRecord.setText("Start Capture");
            isRecording = false;
        } else {
            // Start
            mCameraHelper.startRecordingVideo();
            // 同时开始记录 Sensor 数据
            File sensorFile = new File(FileUtils.getDataDir(this, "SensorData"), "SENS_" + FileUtils.getTimestamp() + ".csv");
            mSensorHelper.startRecording(sensorFile);

            btnRecord.setText("Stop Capture");
            isRecording = true;
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