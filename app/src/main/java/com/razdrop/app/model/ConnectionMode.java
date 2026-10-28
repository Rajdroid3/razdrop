package com.razdrop.app.model;

public enum ConnectionMode {
    WIFI_DIRECT("Wi-Fi Direct"),
    LOCAL_HOTSPOT("Local Hotspot"),
    LOCAL_LAN("Local Wi-Fi"),
    MANUAL_IP("Direct IP");

    private final String displayName;

    ConnectionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
