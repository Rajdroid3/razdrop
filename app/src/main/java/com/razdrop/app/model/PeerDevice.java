package com.razdrop.app.model;

import java.io.Serializable;
import java.util.Objects;

public class PeerDevice implements Serializable {
    private String id;
    private String name;
    private DeviceType type;
    private String ipAddress;
    private int port;
    private ConnectionMode connectionMode;
    private long lastSeenMs;
    private String pinCode;

    public PeerDevice() {}

    public PeerDevice(String id, String name, DeviceType type, String ipAddress, int port, ConnectionMode mode) {
        this.id = id;
        this.name = name;
        this.type = type != null ? type : DeviceType.PHONE;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectionMode = mode != null ? mode : ConnectionMode.LOCAL_LAN;
        this.lastSeenMs = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : "Unknown Device"; }
    public void setName(String name) { this.name = name; }

    public DeviceType getType() { return type != null ? type : DeviceType.PHONE; }
    public void setType(DeviceType type) { this.type = type; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public ConnectionMode getConnectionMode() { return connectionMode != null ? connectionMode : ConnectionMode.LOCAL_LAN; }
    public void setConnectionMode(ConnectionMode connectionMode) { this.connectionMode = connectionMode; }

    public long getLastSeenMs() { return lastSeenMs; }
    public void setLastSeenMs(long lastSeenMs) { this.lastSeenMs = lastSeenMs; }

    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }

    public String getDisplayAddress() {
        if (ipAddress == null) return "";
        return ipAddress + (port > 0 ? ":" + port : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerDevice that = (PeerDevice) o;
        if (id != null && that.id != null) {
            return id.equals(that.id);
        }
        return Objects.equals(ipAddress, that.ipAddress) && port == that.port;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : Objects.hash(ipAddress, port);
    }
}
