package com.example.mobiledatacapturekit;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SensorHelper implements SensorEventListener {
    private final SensorManager mSensorManager;
    private final SensorCallback mCallback;
    private File mLogFile;
    private boolean isRecording = false;

    public interface SensorCallback {
        void onSensorDataUpdated(String sensorName, String dataText);
    }

    public SensorHelper(Context context, SensorCallback callback) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mCallback = callback;
    }

    public void startSensors() {
        registerSensor(Sensor.TYPE_ACCELEROMETER);
        registerSensor(Sensor.TYPE_GYROSCOPE);
        registerSensor(Sensor.TYPE_PRESSURE);
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void registerSensor(int type) {
        Sensor sensor = mSensorManager.getDefaultSensor(type);
        if (sensor != null) {
            //传感器频率修改
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    public void stopSensors() {
        mSensorManager.unregisterListener(this);
    }

    public void startRecording(File logFile) {
        mLogFile = logFile;
        isRecording = true;
        // 写入CSV头
        FileUtils.appendTextToFile(mLogFile, "Timestamp,SensorType,X,Y,Z,Value\n");
    }

    public void stopRecording() {
        isRecording = false;
        mLogFile = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        String name = "";
        String displayVal = "";
        String csvLine = "";
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            name = "Accelerometer";
            displayVal = String.format("X:%.2f Y:%.2f Z:%.2f", event.values[0], event.values[1], event.values[2]);
            csvLine = String.format("%s,ACC,%.4f,%.4f,%.4f,\n", timestamp, event.values[0], event.values[1], event.values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            name = "Gyroscope";
            displayVal = String.format("X:%.2f Y:%.2f Z:%.2f", event.values[0], event.values[1], event.values[2]);
            csvLine = String.format("%s,GYRO,%.4f,%.4f,%.4f,\n", timestamp, event.values[0], event.values[1], event.values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            name = "Pressure";
            displayVal = event.values[0] + " hPa";
            csvLine = String.format("%s,PRESS,,,%.4f\n", timestamp, event.values[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            name = "Magnetic";
            displayVal = String.format("X:%.2f Y:%.2f Z:%.2f", event.values[0], event.values[1], event.values[2]);
            csvLine = String.format("%s,MAG,%.4f,%.4f,%.4f,\n", timestamp, event.values[0], event.values[1], event.values[2]);
        }

        if (!name.isEmpty()) {
            mCallback.onSensorDataUpdated(name, displayVal);
            if (isRecording && mLogFile != null) {
                FileUtils.appendTextToFile(mLogFile, csvLine);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}