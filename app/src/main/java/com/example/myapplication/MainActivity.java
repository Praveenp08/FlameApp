package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import com.example.myapplication.gl.GLRenderer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private GLSurfaceView glSurfaceView;
    private GLRenderer glRenderer;
    private Button toggleButton;
    private boolean showEdges = true;

    static {
        System.loadLibrary("native-lib");
        // System.loadLibrary("opencv_java4"); // Uncomment if needed
    }

    // JNI native method declaration
    public native byte[] processFrameJNI(byte[] yuvData, int width, int height, boolean showEdges);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurfaceView);
        toggleButton = findViewById(R.id.toggleButton);

        glSurfaceView.setEGLContextClientVersion(2); // OpenGL ES 2.0
        glRenderer = new GLRenderer(this);
        glSurfaceView.setRenderer(glRenderer);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEdges = !showEdges;
                toggleButton.setText(showEdges ? "Show Raw" : "Show Edges");
            }
        });

        // Camera permission check
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startBackgroundThread();
            setupCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBackgroundThread();
            setupCamera();
        } else {
            finish();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Size[] yuvSizes = null;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            }
            int width;
            int height;
            // Prefer a reasonable preview size (≤1280x720), fallback to the smallest available
            if (yuvSizes != null && yuvSizes.length > 0) {
                Size chosen = null;
                for (Size s : yuvSizes) {
                    if (s.getWidth() <= 1280 && s.getHeight() <= 720) {
                        if (chosen == null || (s.getWidth() > chosen.getWidth() && s.getHeight() > chosen.getHeight())) {
                            chosen = s;
                        }
                    }
                }
                if (chosen == null) {
                    // fallback: pick the smallest available
                    chosen = yuvSizes[0];
                    for (Size s : yuvSizes) {
                        if (s.getWidth() * s.getHeight() < chosen.getWidth() * chosen.getHeight()) {
                            chosen = s;
                        }
                    }
                }
                width = chosen.getWidth();
                height = chosen.getHeight();
            } else {
                width = 640;
                height = 480;
            }

            // Use at least 3 images for buffer queue for smoother operation
            imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireNextImage();
                    if (image != null) {
                        byte[] yuvBytes = getDataFromImage(image, width, height);
                        image.close(); // Always close the image ASAP!
                        if (yuvBytes != null) {
                            byte[] rgbaBytes = processFrameJNI(yuvBytes, width, height, showEdges);
                            Log.d("CameraDebug", "Got processed frame, bytes: " + (rgbaBytes != null ? rgbaBytes.length : 0));
                            if (rgbaBytes != null && rgbaBytes.length == width * height * 4) {
                                glRenderer.updateFrame(rgbaBytes, width, height);
                                glSurfaceView.requestRender();
                            } else {
                                Log.e("CameraDebug", "RGBA buffer is null or has wrong length!");
                            }
                        }
                    }
                } catch (Exception e) {
                    if (image != null) image.close();
                    e.printStackTrace();
                }
            }, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            Surface surface = imageReader.getSurface();
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Convert Image (YUV_420_888) to NV21 byte array for OpenCV processing
    // Now does validation to avoid IndexOutOfBoundsException!
    private byte[] getDataFromImage(Image image, int width, int height) {
        if (image.getFormat() != ImageFormat.YUV_420_888) return null;
        Image.Plane[] planes = image.getPlanes();

        // The YUV_420_888 format has 3 planes (Y, U, V)
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        // NV21 requires Y + V + U (U and V swapped compared to standard YUV_420_888)
        int nv21Size = ySize + uSize + vSize;
        byte[] nv21 = new byte[nv21Size];

        try {
            // Defensive: check bounds before copy
            if (nv21.length < (ySize + vSize + uSize)) {
                Log.e("CameraDebug", "NV21 buffer too small! ySize=" + ySize + " vSize=" + vSize + " uSize=" + uSize + " nv21.length=" + nv21.length);
                return null;
            }
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
        } catch (IndexOutOfBoundsException e) {
            Log.e("CameraDebug", "IndexOutOfBounds in getDataFromImage: " + e.getMessage());
            return null;
        }
        return nv21;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundThread();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}