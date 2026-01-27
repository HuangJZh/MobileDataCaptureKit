package com.example.mobiledatacapturekit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.Image;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {
    private static final String TAG = "FileUtils";

    // 获取App私有数据目录（不需要权限，不会污染相册）
    public static File getDataDir(Context context, String subDir) {
        File dir = new File(context.getExternalFilesDir(null), subDir);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String getTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    // 保存图片到相册 (MediaStore)
    public static void saveImageToGallery(Context context, Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        image.close();

        String fileName = "IMG_" + getTimestamp() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera2Test");

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            if (uri != null) {
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) {
                        os.write(bytes);
                        Log.d(TAG, "Image saved to gallery: " + fileName);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 将视频文件信息插入相册数据库（让视频在相册可见）
    public static void saveVideoToGallery(Context context, File videoFile) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());

        context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        Log.d(TAG, "Video linked to gallery: " + videoFile.getName());
    }

    // 追加文本到文件 (用于传感器/Metadata日志)
    public static void appendTextToFile(File file, String text) {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.append(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}