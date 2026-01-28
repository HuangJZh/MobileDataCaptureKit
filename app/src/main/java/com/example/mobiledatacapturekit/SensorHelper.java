package com.example.mobiledatacapturekit;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SensorHelper implements SensorEventListener {
    private final SensorManager mSensorManager;
    private final SensorCallback mCallback;
    private File mLogFile;
    private volatile boolean isRecording = false;

    // 后台线程，避免卡顿
    private HandlerThread mSensorThread;
    private Handler mSensorHandler;

    // 复用对象，减少GC
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public interface SensorCallback {
        void onSensorDataUpdated(String sensorName, String dataText);
    }

    public SensorHelper(Context context, SensorCallback callback) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mCallback = callback;
    }

    public void startSensors() {
        mSensorThread = new HandlerThread("SensorThread");
        mSensorThread.start();
        mSensorHandler = new Handler(mSensorThread.getLooper());

        // 分别注册4个传感器，使用不同的频率
        registerSensor(Sensor.TYPE_ACCELEROMETER, AppConfig.ACCELEROMETER_FREQUENCY);
        registerSensor(Sensor.TYPE_GYROSCOPE, AppConfig.GYROSCOPE_FREQUENCY);
        registerSensor(Sensor.TYPE_PRESSURE, AppConfig.PRESSURE_FREQUENCY);
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD, AppConfig.MAGNETIC_FREQUENCY);
    }

    private void registerSensor(int type, int frequency) {
        Sensor sensor = mSensorManager.getDefaultSensor(type);
        if (sensor != null) {
            // 在 mSensorHandler 线程中回调
            mSensorManager.registerListener(this, sensor, frequency, mSensorHandler);
        }
    }

    public void stopSensors() {
        mSensorManager.unregisterListener(this);
        if (mSensorThread != null) {
            mSensorThread.quitSafely();
            try {
                mSensorThread.join();
                mSensorThread = null;
                mSensorHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startRecording(File logFile) {
        mLogFile = logFile;
        if (mSensorHandler != null) {
            mSensorHandler.post(() -> FileUtils.appendTextToFile(mLogFile, "Timestamp(formatted),SensorType,X,Y,Z,Value\n"));
        }
        isRecording = true;
    }

    public void stopRecording() {
        isRecording = false;
        mLogFile = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 运行在后台线程
        String name = "";
        String timestamp = mDateFormat.format(new Date());
        float v0 = event.values[0];

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            name = "Accelerometer";
            processSensorData(name, "ACC", timestamp, event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            name = "Gyroscope";
            processSensorData(name, "GYRO", timestamp, event);
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            name = "Pressure";
            String displayVal = v0 + " hPa";
            if (isRecording && mLogFile != null) {
                String csvLine = String.format(Locale.getDefault(), "%s,PRESS,,,%.4f\n", timestamp, v0);
                FileUtils.appendTextToFile(mLogFile, csvLine);
            }
            mCallback.onSensorDataUpdated(name, displayVal);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            name = "Magnetic";
            processSensorData(name, "MAG", timestamp, event);
        }
    }

    private void processSensorData(String uiName, String csvTag, String timestamp, SensorEvent event) {
        if (isRecording && mLogFile != null) {
            String csvLine = String.format(Locale.getDefault(), "%s,%s,%.4f,%.4f,%.4f,\n",
                    timestamp, csvTag, event.values[0], event.values[1], event.values[2]);
            FileUtils.appendTextToFile(mLogFile, csvLine);
        }

        // 生成 UI 显示字符串
        String displayVal = String.format(Locale.getDefault(), "X:%.2f Y:%.2f Z:%.2f", event.values[0], event.values[1], event.values[2]);
        mCallback.onSensorDataUpdated(uiName, displayVal);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}