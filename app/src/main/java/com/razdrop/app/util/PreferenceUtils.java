package com.razdrop.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.reflect.TypeToken;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.network.protocol.RazPacket;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PreferenceUtils {
    private static final String PREF_NAME = "razdrop_preferences";
    private static final String KEY_DEVICE_NAME = "pref_device_name";
    private static final String KEY_CUSTOM_SAVE_URI = "pref_save_uri";
    private static final String KEY_CUSTOM_SAVE_PATH = "pref_save_path";
    private static final String KEY_LAST_PHONE_IP = "pref_last_phone_ip";
    private static final String KEY_AUTO_ACCEPT = "pref_auto_accept";
    private static final String KEY_HISTORY = "pref_transfer_history";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getDeviceName(Context context) {
        return getPrefs(context).getString(KEY_DEVICE_NAME, null);
    }

    public static void setDeviceName(Context context, String name) {
        getPrefs(context).edit().putString(KEY_DEVICE_NAME, name).apply();
    }

    public static String getCustomSaveUri(Context context) {
        return getPrefs(context).getString(KEY_CUSTOM_SAVE_URI, null);
    }

    public static void setCustomSaveUri(Context context, String uri) {
        getPrefs(context).edit().putString(KEY_CUSTOM_SAVE_URI, uri).apply();
    }

    public static String getCustomSavePath(Context context) {
        return getPrefs(context).getString(KEY_CUSTOM_SAVE_PATH, null);
    }

    public static void setCustomSavePath(Context context, String path) {
        getPrefs(context).edit().putString(KEY_CUSTOM_SAVE_PATH, path).apply();
    }

    public static String getLastPhoneIp(Context context) {
        return getPrefs(context).getString(KEY_LAST_PHONE_IP, null);
    }

    public static void setLastPhoneIp(Context context, String ip) {
        getPrefs(context).edit().putString(KEY_LAST_PHONE_IP, ip).apply();
    }

    public static boolean isAutoAcceptEnabled(Context context) {
        // Default to true for frictionless file receiving across local devices
        return getPrefs(context).getBoolean(KEY_AUTO_ACCEPT, true);
    }

    public static void setAutoAcceptEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_ACCEPT, enabled).apply();
    }

    public static List<TransferBatch> getTransferHistory(Context context) {
        String json = getPrefs(context).getString(KEY_HISTORY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type listType = new TypeToken<ArrayList<TransferBatch>>() {}.getType();
            List<TransferBatch> list = RazPacket.getGson().fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void saveToHistory(Context context, TransferBatch batch) {
        if (batch == null) return;
        List<TransferBatch> history = getTransferHistory(context);
        history.add(0, batch);
        // Keep last 50
        if (history.size() > 50) {
            history = history.subList(0, 50);
        }
        String json = RazPacket.getGson().toJson(history);
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply();
    }
}
