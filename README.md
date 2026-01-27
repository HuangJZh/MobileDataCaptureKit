# MobileDataCaptureKit

**MobileDataCaptureKit** is a lightweight, modular Android application designed for researchers to capture **synchronized camera and sensor data** using a smartphone.

This tool integrates the **Android Camera2 API** with multiple onboard IMU sensors to record high-quality video and time-aligned sensor streams. It is optimized for research in **mobile sensing, computer vision (SLAM/VIO), robotics, and dataset collection**.

---

## 👥 Authors

**黄俊植（Junzhi Huang）** – Shanghai Jiao Tong University — （Email: biscu0@sjtu.edu.cn）

**王士壮（Shizhuang Wang）** – Shanghai Jiao Tong University — （Email: sz.wang@sjtu.edu.cn）

---

## ✨ Features

- **Advanced Camera2 API Integration**
    - Records video (H.264/MP4) with configurable parameters.
    - Captures high-resolution still images.
    - **Frame-Level Metadata**: Logs Timestamp, Exposure Time, ISO, and Focal Length for every video frame to a CSV file.

- **Multi-Sensor Data Logging**
    - Synchronously records data from:
        - Accelerometer
        - Gyroscope
        - Magnetometer
        - Pressure Sensor (Barometer)
    - Data is saved in CSV format with high-precision timestamps.

- **Modern Android Storage Support**
    - Fully compatible with **Android 10+ Scoped Storage**.
    - Videos and Images are automatically saved to the system **Gallery**.
    - Raw sensor data files are accessible via the app's private external storage (`/Android/data/...`).

- **Modular Architecture**
    - Refactored codebase separating Camera, Sensor, and File logic for easy extension and maintenance.

---

## 📂 Project Structure

```text
/app
├── java/com/example/mobiledatacapturekit
│   ├── MainActivity.java      # UI Controller & Permission handling
│   ├── CameraHelper.java      # Camera2 API & MediaRecorder logic
│   ├── SensorHelper.java      # SensorManager & IMU data processing
│   └── FileUtils.java         # File I/O & MediaStore integration
│
├── AndroidManifest.xml        # Permissions & Hardware feature declarations
└── res/layout
    └── activity_camera.xml    # User Interface layout
```

---

## 📊 Data Output Format

The application generates the following files during recording:

### 1. Video Metadata (`META_yyyymmdd_HHmmss.csv`)
Logs camera properties for each frame synchronized with the video.
```csv
Frame, Timestamp(ms), Exposure(ns), ISO, FocalLen
0,     1706334500123, 20000000,     100, 4.5
1,     1706334500156, 20000000,     100, 4.5
...
```

### 2. Sensor Data (`SENS_yyyymmdd_HHmmss.csv`)
Logs high-frequency IMU data.
```csv
Timestamp, SensorType, X,      Y,      Z,      Value
12:35:01,  ACC,        0.123,  9.81,   0.05,
12:35:01,  GYRO,       0.01,   -0.02,  0.00,
12:35:01,  PRESS,      ,       ,       ,       1013.25
...
```

---

## 🛠 Requirements

- **Android Studio** (Flamingo or later recommended)
- **Android SDK Platform 33** (Compile SDK)
- **Minimum Android Version:** Android 10 (API Level 29)
- **Device:** Smartphone with Camera2 API support and relevant sensors.

---

## 🚀 Getting Started

### 1. Clone the repository
```bash
git clone https://github.com/HuangJZh/MobileDataCaptureKit.git
```

### 2. Open in Android Studio
* Select **File > Open** and choose the `MobileDataCaptureKit` directory.
* Allow Gradle to sync and download dependencies.

### 3. Build & Run
1. Connect an Android device via USB.
2. Ensure "Developer Options" and "USB Debugging" are enabled on the phone.
3. Click **Run ▶** in Android Studio.

### 4. Permissions
Upon the first launch, grant the following permissions:
* **Camera** (for video/photo)
* **Microphone** (for audio recording)
* *Note: Storage permission is not required for Android 10+ due to Scoped Storage implementation.*

---

## 📝 License

This project is open-source and available for research and educational purposes.