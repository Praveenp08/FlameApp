cmake_minimum_required(VERSION 3.4.1)
project("FlameApp")

set(OpenCV_DIR /Users/praveenyadav/Downloads/OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(native-lib SHARED native-lib.cpp)
target_link_libraries(native-lib ${OpenCV_LIBS} log)