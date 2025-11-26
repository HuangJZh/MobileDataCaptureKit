# MobileDataCaptureKit

A lightweight Android application designed for researchers to capture **synchronized camera and sensor data** using a smartphone.  
This tool integrates the **Camera2 API** with multiple onboard sensors, making it ideal for research in **mobile sensing, computer vision, robotics, AR/VR, environment sensing**, and data-driven experiments.

---

##  Features

- 📸 **Camera2 API Support**  
  Capture image frames or preview data using the Android Camera2 system.

- 📱 **Multi-Sensor Data Collection**  
  Supports common smartphone sensors:
    - Accelerometer
    - Gyroscope
    - Magnetometer
    - Pressure sensor
    - (and easily extensible)

-  **Synchronized Data Streams**  
  Camera and sensor data are captured with timestamps to support time-aligned analysis.

-  **Lightweight & Easy to Modify**  
  A simple codebase designed to be forked, extended, and integrated into research workflows.

-  **Research-Oriented**  
  Useful for projects in mobile sensing, SLAM, activity recognition, sensor fusion, environment mapping, and more.

---

##  Project Structure

/app
├── java/.../MainActivity.java # Main UI and camera preview
├── java/.../CameraCoreManager.java # Core Camera2 logic
├── AndroidManifest.xml # Permissions and sensor declarations
├── res/... # Layouts & resources


---

##  Requirements

- **Android Studio Flamingo or later**
- **Android 8.0+ (API 26+)**
- Device with camera + sensors (accelerometer, gyro, etc.)

---

##  Getting Started

### 1. Clone the repository
https://github.com/HuangJZh/MobileDataCaptureKit.git
### 2. Open in Android Studio
Select “Open an Existing Project” and choose the cloned directory.

### 3. Build & Run
Connect an Android device → click Run ▶ in Android Studio.

⚠️ Note: Some sensors may not be available on all devices.
