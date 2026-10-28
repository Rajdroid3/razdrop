package com.razdrop.app.model;

public enum DeviceType {
    PHONE,
    TABLET,
    TV,
    DESKTOP,
    UNKNOWN;

    public static DeviceType fromInt(int val) {
        switch (val) {
            case 1: return PHONE;
            case 2: return TABLET;
            case 3: return TV;
            case 4: return DESKTOP;
            default: return UNKNOWN;
        }
    }

    public int toInt() {
        switch (this) {
            case PHONE: return 1;
            case TABLET: return 2;
            case TV: return 3;
            case DESKTOP: return 4;
            default: return 0;
        }
    }
}
