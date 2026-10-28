package com.razdrop.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.razdrop.app.R;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class QrScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "RazDrop-QrScan";
    private static final int REQUEST_CAMERA = 5001;

    public static final String EXTRA_SCANNED_IP = "scanned_ip";
    public static final String EXTRA_SCANNED_PORT = "scanned_port";
    public static final String EXTRA_SCANNED_PIN = "scanned_pin";
    public static final String EXTRA_SCANNED_NAME = "scanned_name";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private View laserView;
    private ImageButton btnTorch;

    private Camera camera;
    private boolean isTorchOn = false;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isDecoded = new AtomicBoolean(false);
    private MultiFormatReader multiFormatReader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable autoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (camera != null && !isDecoded.get()) {
                try {
                    camera.autoFocus((success, cam) -> {});
                } catch (Exception ignored) {}
                mainHandler.postDelayed(this, 2000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        initViews();
        setupDecoder();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surface_camera);
        laserView = findViewById(R.id.view_laser);
        btnTorch = findViewById(R.id.btn_scan_torch);
        ImageButton btnBack = findViewById(R.id.btn_scan_back);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnBack.setOnClickListener(v -> finish());
        btnTorch.setOnClickListener(v -> toggleTorch());

        surfaceView.setOnClickListener(v -> triggerFocus());

        startLaserAnimation();
    }

    private void triggerFocus() {
        if (camera != null) {
            try {
                camera.cancelAutoFocus();
                camera.autoFocus((success, cam) -> {});
            } catch (Exception ignored) {}
        }
    }

    private void startLaserAnimation() {
        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f
        );
        animation.setDuration(2000);
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        animation.setInterpolator(new LinearInterpolator());
        laserView.startAnimation(animation);
    }

    private void setupDecoder() {
        multiFormatReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        multiFormatReader.setHints(hints);
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            List<String> flashModes = params.getSupportedFlashModes();
            if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                if (isTorchOn) {
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    isTorchOn = false;
                } else {
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    isTorchOn = true;
                }
                camera.setParameters(params);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error toggling torch", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewDisplay(surfaceHolder);
                camera.setOneShotPreviewCallback(this);
                camera.startPreview();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    private void startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            if (camera == null) {
                camera = Camera.open();
            }

            camera.setDisplayOrientation(90);

            Camera.Parameters parameters = camera.getParameters();

            // Pick sweet spot preview size (e.g. 1280x720 or 960x540)
            Camera.Size previewSize = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), 1280, 720);
            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
            }

            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }

            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setOneShotPreviewCallback(this);
            camera.startPreview();

            mainHandler.postDelayed(autoFocusRunnable, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetW, int targetH) {
        if (sizes == null) return null;
        Camera.Size optimal = null;
        int minDiff = Integer.MAX_VALUE;
        for (Camera.Size size : sizes) {
            int diff = Math.abs(size.width - targetW) + Math.abs(size.height - targetH);
            if (diff < minDiff) {
                optimal = size;
                minDiff = diff;
            }
        }
        return optimal != null ? optimal : sizes.get(0);
    }

    private void stopCamera() {
        mainHandler.removeCallbacks(autoFocusRunnable);
        if (camera != null) {
            try {
                camera.setOneShotPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (isDecoded.get() || camera == null || data == null) return;

        if (isProcessing.compareAndSet(false, true)) {
            new Thread(() -> {
                Result decodedResult = null;
                try {
                    Camera.Size size = camera.getParameters().getPreviewSize();
                    int width = size.width;
                    int height = size.height;

                    // Direct raw YUV luminance source (zero copy, instant speed!)
                    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                            data, width, height, 0, 0, width, height, false
                    );

                    // 1. Try standard orientation
                    try {
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        decodedResult = multiFormatReader.decodeWithState(bitmap);
                    } catch (Exception ignored) {}

                    // 2. Try inverted (white-on-dark modules or high reflection)
                    if (decodedResult == null) {
                        try {
                            BinaryBitmap inverted = new BinaryBitmap(new HybridBinarizer(source.invert()));
                            decodedResult = multiFormatReader.decodeWithState(inverted);
                        } catch (Exception ignored) {}
                    }

                    // 3. Try rotated 90 degrees if camera sensor orientation differs
                    if (decodedResult == null) {
                        try {
                            LuminanceSource rotated = source.rotateCounterClockwise();
                            BinaryBitmap rotatedBitmap = new BinaryBitmap(new HybridBinarizer(rotated));
                            decodedResult = multiFormatReader.decodeWithState(rotatedBitmap);
                        } catch (Exception ignored) {}
                    }

                    if (decodedResult != null && decodedResult.getText() != null && !decodedResult.getText().isEmpty()) {
                        handleDecodedResult(decodedResult.getText());
                        return;
                    }
                } catch (Exception ignored) {
                } finally {
                    multiFormatReader.reset();
                    isProcessing.set(false);
                    if (!isDecoded.get() && camera != null) {
                        mainHandler.post(() -> {
                            try {
                                camera.setOneShotPreviewCallback(QrScanActivity.this);
                            } catch (Exception ignored) {}
                        });
                    }
                }
            }).start();
        }
    }

    private void handleDecodedResult(String rawText) {
        if (!isDecoded.compareAndSet(false, true)) return;

        Log.d(TAG, "QR Decoded successfully: " + rawText);

        mainHandler.post(() -> {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(100);
            } catch (Exception ignored) {}

            String ip = null;
            int port = 58920;
            String pin = null;
            String name = "Android TV";

            if (rawText.startsWith("razdrop://connect")) {
                Uri uri = Uri.parse(rawText);
                ip = uri.getQueryParameter("ip");
                String portStr = uri.getQueryParameter("port");
                if (portStr != null) {
                    try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
                }
                pin = uri.getQueryParameter("pin");
                String nameParam = uri.getQueryParameter("name");
                if (nameParam != null && !nameParam.isEmpty()) {
                    name = nameParam;
                }
            } else if (rawText.contains(":")) {
                String[] parts = rawText.split(":");
                ip = parts[0];
                try { port = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
            } else {
                ip = rawText.trim();
            }

            if (ip == null || ip.isEmpty()) {
                Toast.makeText(this, "Unrecognized QR code", Toast.LENGTH_SHORT).show();
                isDecoded.set(false);
                return;
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SCANNED_IP, ip);
            resultIntent.putExtra(EXTRA_SCANNED_PORT, port);
            resultIntent.putExtra(EXTRA_SCANNED_PIN, pin);
            resultIntent.putExtra(EXTRA_SCANNED_NAME, name);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission required to scan TV QR code", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }
}
