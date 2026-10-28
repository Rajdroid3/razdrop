package com.razdrop.app.network.hotspot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.razdrop.app.util.NetworkUtils;

public class LocalHotspotManager {
    private static final String TAG = "RazDrop-Hotspot";

    private final Context context;
    private final WifiManager wifiManager;
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private HotspotStateListener listener;
    private String ssid;
    private String password;
    private String hostIp = "192.168.49.1"; // Standard Android Hotspot Gateway IP
    private boolean isHotspotActive = false;

    public interface HotspotStateListener {
        void onHotspotStarted(String ssid, String password, String hostIp);
        void onHotspotStopped();
        void onHotspotFailed(int reason);
    }

    public LocalHotspotManager(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    public void setListener(HotspotStateListener listener) {
        this.listener = listener;
    }

    public boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wifiManager != null;
    }

    public boolean isActive() {
        return isHotspotActive;
    }

    public String getSsid() { return ssid; }
    public String getPassword() { return password; }
    public String getHostIp() { return hostIp; }

    @SuppressLint("MissingPermission")
    public void startHotspot() {
        if (!isSupported()) {
            if (listener != null) listener.onHotspotFailed(-1);
            return;
        }

        if (isHotspotActive) {
            if (listener != null) listener.onHotspotStarted(ssid, password, hostIp);
            return;
        }

        try {
            wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation res) {
                    reservation = res;
                    isHotspotActive = true;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            android.net.wifi.SoftApConfiguration softApConfig = res.getSoftApConfiguration();
                            if (softApConfig != null) {
                                ssid = softApConfig.getSsid();
                                password = softApConfig.getPassphrase();
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Failed reading softApConfiguration", t);
                        }
                    }

                    if (ssid == null) {
                        WifiConfiguration wifiConfig = res.getWifiConfiguration();
                        if (wifiConfig != null) {
                            ssid = wifiConfig.SSID;
                            password = wifiConfig.preSharedKey;
                        }
                    }

                    // Discover IP
                    String localIp = NetworkUtils.getLocalIpAddress();
                    if (localIp != null && !localIp.isEmpty()) {
                        hostIp = localIp;
                    }

                    Log.d(TAG, "Local hotspot started: SSID=" + ssid + " Pass=" + password + " IP=" + hostIp);
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                listener.onHotspotStarted(ssid, password, hostIp));
                    }
                }

                @Override
                public void onStopped() {
                    Log.d(TAG, "Local hotspot stopped");
                    isHotspotActive = false;
                    reservation = null;
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onHotspotStopped());
                    }
                }

                @Override
                public void onFailed(int reason) {
                    Log.e(TAG, "Local hotspot failed with reason: " + reason);
                    isHotspotActive = false;
                    reservation = null;
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onHotspotFailed(reason));
                    }
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied starting hotspot", e);
            if (listener != null) listener.onHotspotFailed(-2);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting hotspot", e);
            if (listener != null) listener.onHotspotFailed(-3);
        }
    }

    public synchronized void stopHotspot() {
        if (reservation != null) {
            try {
                reservation.close();
            } catch (Exception ignored) {}
            reservation = null;
        }
        isHotspotActive = false;
        if (listener != null) {
            listener.onHotspotStopped();
        }
    }
}
