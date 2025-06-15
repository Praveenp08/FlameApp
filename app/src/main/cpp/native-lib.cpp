#include <jni.h>
#include <vector>
#include <cstring>
#include <opencv2/imgproc.hpp>



extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_myapplication_MainActivity_processFrameJNI(JNIEnv *env, jobject thiz,
                                                            jbyteArray yuv_data, jint width,
                                                            jint height, jboolean show_edges) {

    // Get the YUV data from Java
    jsize yuvLength = env->GetArrayLength(yuv_data);
    std::vector<jbyte> yuvBytes(yuvLength);
    env->GetByteArrayRegion(yuv_data, 0, yuvLength, yuvBytes.data());

    // Output RGBA buffer
    int outputLength = width * height * 4;
    std::vector<jbyte> rgbaBytes(outputLength);

    cv::Mat yuvImg(height + height / 2, width, CV_8UC1, yuvBytes.data());
    cv::Mat rgbaImg(height, width, CV_8UC4);
    cv::Mat rotatedImg;
    cv::rotate(rgbaImg, rotatedImg, cv::ROTATE_90_CLOCKWISE);

    if (show_edges) {
        // Convert YUV to RGBA
        cv::cvtColor(yuvImg, rgbaImg, cv::COLOR_YUV2RGBA_NV21);

        // Edge detection (optional)
        cv::Mat gray, edges;
        cv::cvtColor(rgbaImg, gray, cv::COLOR_RGBA2GRAY);
        cv::Canny(gray, edges, 80, 150);
        cv::cvtColor(edges, rgbaImg, cv::COLOR_GRAY2RGBA);
    } else {
        cv::cvtColor(yuvImg, rgbaImg, cv::COLOR_YUV2RGBA_NV21);
    }
    std::memcpy(rgbaBytes.data(), rgbaImg.data, outputLength);

    // Return the RGBA buffer to Java
    jbyteArray result = env->NewByteArray(outputLength);
    env->SetByteArrayRegion(result, 0, outputLength, rgbaBytes.data());
    return result;
}