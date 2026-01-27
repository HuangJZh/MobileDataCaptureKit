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
    private volatile boolean isRecording = false; // 多线程访问需volatile

    // 优化1: 引入后台线程处理传感器数据
    private HandlerThread mSensorThread;
    private Handler mSensorHandler;

    // 优化2: 复用 SimpleDateFormat，避免高频创建
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public interface SensorCallback {
        void onSensorDataUpdated(String sensorName, String dataText);
    }

    public SensorHelper(Context context, SensorCallback callback) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mCallback = callback;
    }

    public void startSensors() {
        // 启动传感器专属后台线程
        mSensorThread = new HandlerThread("SensorThread");
        mSensorThread.start();
        mSensorHandler = new Handler(mSensorThread.getLooper());

        registerSensor(Sensor.TYPE_ACCELEROMETER);
        registerSensor(Sensor.TYPE_GYROSCOPE);
        registerSensor(Sensor.TYPE_PRESSURE);
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void registerSensor(int type) {
        Sensor sensor = mSensorManager.getDefaultSensor(type);
        if (sensor != null) {
            // 关键修改: 将 Handler 传入 registerListener，使其在后台线程回调
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, mSensorHandler);
        }
    }

    public void stopSensors() {
        mSensorManager.unregisterListener(this);
        // 停止线程
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
        // 写入CSV头 (注意: 这里可能会有并发写问题，建议也在Handler中执行，或者简单加锁，这里简单处理)
        if (mSensorHandler != null) {
            mSensorHandler.post(() -> FileUtils.appendTextToFile(mLogFile, "Timestamp,SensorType,X,Y,Z,Value\n"));
        }
        isRecording = true;
    }

    public void stopRecording() {
        isRecording = false;
        mLogFile = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 此方法现在运行在 SensorThread 中，不会卡住 UI

        // 优化3: 仅在需要时进行计算 (虽然移到了后台线程，但能省电)

        String name = "";
        // 用于UI显示的简单数值（减少字符串拼接开销）
        float v0 = event.values[0];

        // 获取时间戳 (复用 formatter)
        String timestamp = mDateFormat.format(new Date());

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            name = "Accelerometer";
            // 数据处理逻辑...
            processSensorData(name, "ACC", timestamp, event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            name = "Gyroscope";
            processSensorData(name, "GYRO", timestamp, event);
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            name = "Pressure";
            String displayVal = v0 + " hPa";
            // 只有录制时才拼装长字符串
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

    // 辅助方法减少代码重复
    private void processSensorData(String uiName, String csvTag, String timestamp, SensorEvent event) {
        // UI 回调 (注意：MainActivity 里的实现已经有了 runOnUiThread，所以这里可以直接调)
        // 只有录制时，才进行昂贵的 CSV 格式化
        if (isRecording && mLogFile != null) {
            String csvLine = String.format(Locale.getDefault(), "%s,%s,%.4f,%.4f,%.4f,\n",
                    timestamp, csvTag, event.values[0], event.values[1], event.values[2]);
            FileUtils.appendTextToFile(mLogFile, csvLine);
        }

        // 为了UI显示，还是需要格式化一下，但这个频率极高，其实可以只传递 raw data 让 UI 层去节流格式化
        // 但为了最小改动，保持原样，交给 MainActivity 的节流逻辑去丢弃多余的更新
        String displayVal = String.format(Locale.getDefault(), "X:%.2f Y:%.2f Z:%.2f", event.values[0], event.values[1], event.values[2]);
        mCallback.onSensorDataUpdated(uiName, displayVal);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}