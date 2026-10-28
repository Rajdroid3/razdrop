package com.razdrop.app.util;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotHelper {
    private static final String TAG = "ScreenshotHelper";
    public static final String ACTION_SCREENSHOT = "com.razdrop.app.ACTION_SCREENSHOT";
    public static final String EXTRA_FILENAME = "filename";

    public static BroadcastReceiver registerScreenshotReceiver(final Activity activity) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_SCREENSHOT.equals(intent.getAction())) {
                    if (intent.getBooleanExtra("demo", false)) {
                        android.widget.TextView tvName = activity.findViewById(com.razdrop.app.R.id.tv_tv_name);
                        android.widget.TextView tvIp = activity.findViewById(com.razdrop.app.R.id.tv_tv_ip);
                        if (tvName != null) tvName.setText("Android TV");
                        if (tvIp != null) tvIp.setText("192.168.1.100:58920");
                    }
                    String filename = intent.getStringExtra(EXTRA_FILENAME);
                    capture(activity, filename);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_SCREENSHOT);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public static boolean handleKeyEvent(Activity activity, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_CAMERA ||
                keyCode == KeyEvent.KEYCODE_SYSRQ || // PrintScreen key on USB/BT keyboard
                keyCode == KeyEvent.KEYCODE_MEDIA_RECORD ||
                keyCode == KeyEvent.KEYCODE_GUIDE) {
                capture(activity, null);
                return true;
            }
        }
        return false;
    }

    public static File capture(final Activity activity, final String customFilename) {
        try {
            final View decorView = activity.getWindow().getDecorView();
            int width = decorView.getWidth();
            int height = decorView.getHeight();

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "DecorView not yet laid out (width=" + width + ", height=" + height + ")");
                return null;
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            decorView.draw(canvas);

            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!picturesDir.exists()) {
                picturesDir.mkdirs();
            }

            String filename = customFilename;
            if (filename == null || filename.trim().isEmpty()) {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                filename = "razdrop_" + activity.getClass().getSimpleName() + "_" + timestamp + ".png";
            }
            if (!filename.toLowerCase(Locale.US).endsWith(".png")) {
                filename += ".png";
            }

            File dest = new File(picturesDir, filename);
            try (FileOutputStream out = new FileOutputStream(dest)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }

            Log.i(TAG, "Screenshot saved successfully to: " + dest.getAbsolutePath());
            MediaScannerConnection.scanFile(activity, new String[]{dest.getAbsolutePath()}, new String[]{"image/png"}, null);

            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(activity, "Captured: " + dest.getName(), Toast.LENGTH_SHORT).show()
            );

            return dest;
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screenshot", e);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(activity, "Screenshot failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
            return null;
        }
    }
}
