package com.razdrop.app.util;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;
import com.razdrop.app.model.DeviceType;
import java.security.SecureRandom;

public class DeviceUtils {
    public static boolean isTelevision(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("android.hardware.type.television");
    }

    public static boolean isTablet(Context context) {
        int screenLayout = context.getResources().getConfiguration().screenLayout;
        return (screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static DeviceType getDeviceType(Context context) {
        if (isTelevision(context)) {
            return DeviceType.TV;
        } else if (isTablet(context)) {
            return DeviceType.TABLET;
        } else {
            return DeviceType.PHONE;
        }
    }

    public static String getDeviceName(Context context) {
        String saved = PreferenceUtils.getDeviceName(context);
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim();
        }

        try {
            String sysName = Settings.Global.getString(context.getContentResolver(), "device_name");
            if (sysName != null && !sysName.trim().isEmpty()) {
                return sysName.trim();
            }
        } catch (Exception ignored) {}

        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    public static String generateQuickPin() {
        SecureRandom random = new SecureRandom();
        int pin = 1000 + random.nextInt(9000);
        return String.valueOf(pin);
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) return "";
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) return s;
        return Character.toUpperCase(first) + s.substring(1);
    }
}
