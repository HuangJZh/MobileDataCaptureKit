package com.example.mobiledatacapturekit;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import androidx.preference.PreferenceManager;

public class AppConfig {

    // ==========================================
    // 键名定义
    // ==========================================
    public static final String KEY_VIDEO_FPS = "video_fps";
    public static final String KEY_FOCUS_AUTO = "focus_auto";
    public static final String KEY_EXPOSURE_AUTO = "exposure_auto";
    public static final String KEY_EXPOSURE_TIME = "exposure_time";
    public static final String KEY_ISO = "manual_iso";
    public static final String KEY_FREQ_ACC = "freq_acc";
    // public static final String KEY_FREQ_GYRO = "freq_gyro"; // 已移除
    public static final String KEY_FREQ_MAG = "freq_mag";
    public static final String KEY_FREQ_PRESS = "freq_press";

    // ==========================================
    // 静态默认值
    // ==========================================
    public static final int DEFAULT_FPS = 30;
    public static final String DEFAULT_EXP_TIME = "15000000"; // 15ms
    public static final String DEFAULT_ISO = "800";

    // 固定的非配置项
    public static final int VIDEO_WIDTH = 1920;
    public static final int VIDEO_HEIGHT = 1080;
    public static final int VIDEO_BITRATE = 10000000;
    public static final float FIXED_FOCUS_DISTANCE = 0.0f;

    // ==========================================
    // 动态获取方法
    // ==========================================

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static int getVideoFps(Context context) {
        String val = getPrefs(context).getString(KEY_VIDEO_FPS, String.valueOf(DEFAULT_FPS));
        return Integer.parseInt(val);
    }

    public static boolean isAutoFocus(Context context) {
        return getPrefs(context).getBoolean(KEY_FOCUS_AUTO, false);
    }

    public static boolean isAutoExposure(Context context) {
        return getPrefs(context).getBoolean(KEY_EXPOSURE_AUTO, false);
    }

    public static long getExposureTime(Context context) {
        String val = getPrefs(context).getString(KEY_EXPOSURE_TIME, DEFAULT_EXP_TIME);
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return Long.parseLong(DEFAULT_EXP_TIME);
        }
    }

    public static int getIso(Context context) {
        String val = getPrefs(context).getString(KEY_ISO, DEFAULT_ISO);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_ISO);
        }
    }

    private static int getSensorFreq(Context context, String key, int defaultVal) {
        String val = getPrefs(context).getString(key, String.valueOf(defaultVal));
        return Integer.parseInt(val);
    }

    public static int getAccFreq(Context context) {
        return getSensorFreq(context, KEY_FREQ_ACC, SensorManager.SENSOR_DELAY_FASTEST);
    }

    // 【修改点】陀螺仪强制最快，不从设置读取
    public static int getGyroFreq(Context context) {
        return SensorManager.SENSOR_DELAY_FASTEST;
    }

    public static int getMagFreq(Context context) {
        return getSensorFreq(context, KEY_FREQ_MAG, SensorManager.SENSOR_DELAY_GAME);
    }

    public static int getPressFreq(Context context) {
        return getSensorFreq(context, KEY_FREQ_PRESS, SensorManager.SENSOR_DELAY_NORMAL);
    }
}