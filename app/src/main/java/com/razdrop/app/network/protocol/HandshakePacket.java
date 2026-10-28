package com.razdrop.app.network.protocol;

import com.google.gson.annotations.SerializedName;
import com.razdrop.app.model.DeviceType;

public class HandshakePacket {
    @SerializedName("version")
    private int version = 1;

    @SerializedName("deviceName")
    private String deviceName;

    @SerializedName("deviceType")
    private DeviceType deviceType;

    @SerializedName("pin")
    private String pin;

    @SerializedName("accepted")
    private boolean accepted;

    @SerializedName("message")
    private String message;

    public HandshakePacket() {}

    public HandshakePacket(String deviceName, DeviceType deviceType, String pin) {
        this.version = 1;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.pin = pin;
        this.accepted = true;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
