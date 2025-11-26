package com.example.camera2test;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

import android.os.Handler;
import android.os.HandlerThread;
public class MainActivity extends Activity implements View.OnClickListener{
    private static final String TAG = "CameraDemo";
    private ImageButton mTakePictureBtn;
    private Button videostartstop;

    private CameraCoreManager manager;
    private TextureView mTextureView;

    //Sensors
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor gyroscopeSensor;
    private Sensor pressureSensor;
    private Sensor magneticFieldSensor;
    private TextView accelerometerTextView;
    private TextView gyroscopeTextView;
    private TextView pressureTextView;
    private TextView magneticFieldTextView;
    private Button startStopButton;
    private File storageFile_sensors;

    private boolean isRecording = false;
    private boolean iscapturing = false;
    private int lineCount = 0;
    private static final int MAX_LINES_PER_FILE = 1000000;
    private int fileIndex = 1;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    // 创建一个HandlerThread
    HandlerThread handlerThread = new HandlerThread("DataWritingThread");
    Handler dataWritingHandler;


    public MainActivity() {
        handlerThread.start();
        // 创建一个运行在该HandlerThread中的Handler
        dataWritingHandler = new Handler(handlerThread.getLooper());
    }
    public void writeSensorDataToFileInBackground(final SensorEvent event) {
        // 将写入数据的任务提交给Handler
        dataWritingHandler.post(new Runnable() {
            @Override
            public void run() {
                // 在这里执行写入数据的操作，例如调用writeSensorDataToFile方法
                writeSensorDataToFile(event);
            }
        });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mTakePictureBtn = findViewById(R.id.camera_take_picture);
        mTakePictureBtn.setOnClickListener(this);
        videostartstop= findViewById(R.id.videostartstop);
        mTextureView = findViewById(R.id.texture_view);

        accelerometerTextView = findViewById(R.id.accelerometerTextView);
        gyroscopeTextView = findViewById(R.id.gyroscopeTextView);
        pressureTextView = findViewById(R.id.pressureTextView);
        magneticFieldTextView = findViewById(R.id. magneticFieldTextView);
        startStopButton = findViewById(R.id.startStopButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // Check and request permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        } else {
            setupStorageFile();
        }
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                manager.setSurfaceTexture(surface);
                manager.startPreview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        manager = new CameraCoreManager(this,mTextureView);
        openCameras();
        Log.d(TAG,"onCreate:init");
    }
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            MainActivity.this.onSensorChanged(event); // 调用MainActivity中的onSensorChanged方法
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 这里可以添加适当的实现
        }
    };
    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorEventListener, gyroscopeSensor,SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorEventListener, pressureSensor,SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorEventListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_FASTEST);

    }

//    @Override
//    public void onPause() {
//        super.onPause();
//        sensorManager.unregisterListener((SensorEventListener) this);
//    }

    @Override
    public void onPause() {
        super.onPause();

        // Check if 'this' is an instance of SensorEventListener before unregistering.
        if (this instanceof SensorEventListener) {
            sensorManager.unregisterListener((SensorEventListener) this);
        } else {
            // Handle the case where 'this' is not a SensorEventListener.
            // You may want to log an error or take appropriate action.
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        manager.stopPreview();
    }
    @SuppressLint("SetTextI18n")
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometerSensor) {
            // Process accelerometer data
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            accelerometerTextView.setText("Accelerometer:\nX: " + x + "\nY: " + y + "\nZ: " + z);
        } else if (event.sensor == gyroscopeSensor) {
            // Process gyroscope data
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            gyroscopeTextView.setText("Gyroscope:\nX: " + x + "\nY: " + y + "\nZ: " + z);
        } else if (event.sensor == pressureSensor) {
            // Process pressure data
            float pressure = event.values[0];
            pressureTextView.setText("Pressure: " + pressure +" hPa");
        }else if (event.sensor == magneticFieldSensor) {
            // Process magnetic field data
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            magneticFieldTextView.setText("Magnetic Field:\nX: " + x + "\nY: " + y + "\nZ: " + z);
        }

        // Write sensor data to file
        if (isRecording && storageFile_sensors != null) {
            writeSensorDataToFileInBackground(event);
        }
    }

    @Override
    public void onClick(View v) {
        if(v.getId()==R.id.camera_take_picture) {
            Log.d(TAG, "takepicture");
            manager.captureStillPicture();
        }
        if (iscapturing) {
            iscapturing=false;
            manager.stopRecorder();
            videostartstop.setText("Startcapture");
        } else {
            iscapturing=true;
            manager.startRecorder();
            videostartstop.setText("Stopcapture");
        }
    }

   //sensors
    private void setupStorageFile() {
    String filePath=getExternalFilesDir(null).getPath();
    File directory=new File(filePath+"/SensorData");
    if (!directory.exists()) {
        directory.mkdirs();
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    String fileName = "sensor_data_" + dateFormat.format(new Date()) + ".txt";
    storageFile_sensors = new File(directory, fileName);
    }

    private void writeSensorDataToFile(SensorEvent event) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("HH mm ss.SSS");
        String timestamp = dateFormat.format(new Date());

        int sensorCode = -1;
        DecimalFormat sensorFormat=new DecimalFormat();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            sensorCode = 1;
            String style="00.00000000"; // 2 digits before the decimal point
            sensorFormat.applyPattern(style);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            sensorCode = 2;
            String style="00.00000000"; // 2 digits before the decimal point
            sensorFormat.applyPattern(style);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            sensorCode = 3;
            String style="0000.000000"; // 2 digits before the decimal point
            sensorFormat.applyPattern(style); // 4 digits before the decimal point
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            sensorCode = 4;
            String style="00000.00000"; // 2 digits before the decimal point
            sensorFormat.applyPattern(style);// 5 digits before the decimal point
        }

        StringBuilder sensorData = new StringBuilder(timestamp + "    " + sensorCode + "    ");

        for (float value : event.values) {
            if (value > 0) {
                sensorData.append(" ");

            }
            sensorData.append(sensorFormat.format(value)).append("    ");

        }

        if (sensorCode == 4) {
            // Append two extra pressure values with leading zeros
            sensorData.append(" 00000.00000     00000.00000");
        }

        sensorData.append("\n");


        try {
            // Check if the directory exists, and if not, create it
            if (!Objects.requireNonNull(storageFile_sensors.getParentFile()).exists()) {
                storageFile_sensors.getParentFile().mkdirs();
            }

            FileWriter writer = new FileWriter(storageFile_sensors, true);

            // 检查行数并在需要时创建新文件
            if (lineCount >= MAX_LINES_PER_FILE) {
                writer.close(); // 关闭当前文件
                lineCount = 0;
                fileIndex++;
                setupStorageFile(); // 创建新的存储文件
                writer = new FileWriter(storageFile_sensors, true); // 重新打开新文件
            }

            writer.append(sensorData.toString());
            writer.close();
            lineCount++;

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("SetTextI18n")
    public void onStartStopButtonClick(View view) {
        if (isRecording) {
            stopRecording();
            startStopButton.setText("Startrecord");
        } else {
            isRecording=true;
            startRecording();
            startStopButton.setText("Stoprecord");
        }
    }

    private void startRecording() {
        lineCount = 0;
        fileIndex = 1;
        setupStorageFile();
    }

    private void stopRecording() {

        isRecording = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 相机权限已被授予，可以打开相机
                openCamera();
            } else {
                // 相机权限未被授予，显示一个提示或执行其他操作
                Toast.makeText(this, "Camera permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupStorageFile();
            } else {
                Toast.makeText(this, "Permission denied. App may not function properly.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //permission

    public void openCameras() {
        if (hasCameraPermission()) {
            // 已经有相机权限，可以打开相机
            openCamera();
        } else {
            // 请求相机权限
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission() {
        // 检查是否已经有相机权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
            return cameraPermission == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestCameraPermission() {
        // 请求相机权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private void openCamera() {
        manager = new CameraCoreManager(this,mTextureView);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                manager.setSurfaceTexture(surface);
                manager.startPreview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });


    }



}


