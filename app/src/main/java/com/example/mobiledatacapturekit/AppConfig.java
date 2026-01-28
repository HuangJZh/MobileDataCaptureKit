package com.example.mobiledatacapturekit;

import android.hardware.SensorManager;

public class AppConfig {

    // ==========================================
    // 1. 视频与相机分辨率 (Video Resolution)
    // ==========================================
    public static final int VIDEO_WIDTH = 1920;
    public static final int VIDEO_HEIGHT = 1080;
    public static final int VIDEO_FPS = 30;
    public static final int VIDEO_BITRATE = 10000000; // 10Mbps

    // ==========================================
    // 2. SLAM 核心设置: 对焦与曝光 (Focus & Exposure)
    // ==========================================

    // --- 对焦 (Focus) ---
    // SLAM 必须关闭自动对焦，防止焦距变化导致内参改变
    public static final boolean ENABLE_AUTO_FOCUS = false;
    public static final float FIXED_FOCUS_DISTANCE = 0.0f; // 0.0f = 无穷远

    // --- 曝光 (Exposure) ---
    // SLAM 建议关闭自动曝光，使用短快门防止运动模糊(Motion Blur)
    public static final boolean ENABLE_AUTO_EXPOSURE = false;

    // 曝光时间 (纳秒): 推荐 10ms-15ms (10000000 - 15000000) 室内，<5ms 室外
    public static final long FIXED_EXPOSURE_TIME_NS = 15000000L;

    // ISO: 配合短快门，ISO需要适当提高 (400-1600)
    public static final int FIXED_ISO = 800;

    // ==========================================
    // 3. 传感器采集频率 (Sensor Frequencies)
    // ==========================================
    // SENSOR_DELAY_FASTEST (0us)   ~ 200-500Hz (SLAM 核心)
    // SENSOR_DELAY_GAME    (20ms)  ~ 50Hz
    // SENSOR_DELAY_UI      (60ms)  ~ 16Hz
    // SENSOR_DELAY_NORMAL  (200ms) ~ 5Hz

    public static final int ACCELEROMETER_FREQUENCY = SensorManager.SENSOR_DELAY_FASTEST; // ACC IMU 核心
    public static final int GYROSCOPE_FREQUENCY = SensorManager.SENSOR_DELAY_FASTEST;     // GYRO IMU 核心
    public static final int MAGNETIC_FREQUENCY = SensorManager.SENSOR_DELAY_FASTEST;         // MAG 辅助
    public static final int PRESSURE_FREQUENCY = SensorManager.SENSOR_DELAY_FASTEST;       // PRESS 辅助
}