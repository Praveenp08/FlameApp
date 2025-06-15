# 🚀 FlameApp
 
 A real-time Android camera app featuring native C++/JNI image processing (OpenCV) and efficient OpenGL rendering.
 
 ---
 
 ## ✅ Features Implemented
 
 - 📸 **Real-time camera preview** using Camera2 API
 - ⚡ **Native C++ (JNI) image processing** for high performance
 - 🧊 **OpenCV integration** for efficient frame manipulation (e.g., edge detection)
 - 🎨 **OpenGL rendering** of processed frames
 - 🔄 **Live toggle** between raw and processed (edge-detected) views
 - 🧩 Clean, modular project structure
 
 ---
 
 ## 🖼️ Working Demo
 
 https://github.com/Praveenp08/FlameApp/blob/master/WhatsApp%20Image%202025-06-15%20at%2010.44.34%20AM%20(1).jpeg
https://github.com/Praveenp08/FlameApp/blob/master/WhatsApp%20Image%202025-06-15%20at%2010.44.34%20AM.jpeg
 
 ---
 
 ## ⚙️ Setup Instructions
 
 ### 1. Requirements
 
 - **Android Studio** (latest recommended)
 - **NDK** and **CMake** (install via SDK Manager)
 - **OpenCV for Android** (native)
 
 ### 2. Project Structure
 
 ```
 app/
  ├── src/main/
  │    ├── java/com/example/flameapp/
  │    │     ├── MainActivity.java
  │    │     └── gl/GLRenderer.java
  │    ├── cpp/
  │    │     ├── native-lib.cpp
  │    │     └── CMakeLists.txt
  │    └── res/layout/activity_main.xml
  └── build.gradle
 ```
 
 ### 3. Setup Steps
 
 1. **Install NDK & CMake:**  
    - Android Studio → Preferences → SDK Tools → Check _NDK (Side by side)_ and _CMake_.
 
 2. **OpenCV:**
    - Download [OpenCV Android SDK](https://opencv.org/releases/).
    - Copy `sdk/native/libs` to `app/src/main/jniLibs/`
    - Copy `sdk/native/jni/include` to `app/src/main/cpp/include/`
    - Update `CMakeLists.txt` to link OpenCV native libs.
 
 3. **Configure `CMakeLists.txt`:**
     ```cmake
     cmake_minimum_required(VERSION 3.4.1)
     add_library(native-lib SHARED native-lib.cpp)
     find_library(log-lib log)
     target_link_libraries(native-lib ${log-lib})
     # Uncomment and update if using OpenCV:
     # set(OpenCV_DIR <path-to-opencv-sdk>/sdk/native/jni)
     # find_package(OpenCV REQUIRED)
     # target_link_libraries(native-lib ${OpenCV_LIBS})
     ```
 
 4. **Sync Gradle:**  
    - Confirm `externalNativeBuild` in `app/build.gradle`:
     ```gradle
     android {
         ...
         externalNativeBuild {
             cmake {
                 path "src/main/cpp/CMakeLists.txt"
             }
         }
     }
     ```
 
 5. **Build & Run:**  
    - Connect a device or launch an emulator with a camera
    - Run the app!
 
 ---
 
 ## 🧠 Architecture Overview
 
 ### Frame Flow
 
 1. **Camera2 API** captures YUV frames.
 2. **JNI** bridges Java/Kotlin and native C++ code.
 3. **C++ (JNI) code** (using OpenCV) processes frames (e.g., edge detection, color conversion).
 4. Processed frames are passed back to Java and rendered via **OpenGL**.
 
 ### Key Components
 
 - `MainActivity.java`: Camera handling, JNI calls, UI
 - `GLRenderer.java`: Efficient OpenGL frame rendering
 - `native-lib.cpp`: Native image processing (YUV→RGBA, OpenCV, frame rotation, etc.)
