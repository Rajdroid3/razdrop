package com.razdrop.app.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Locale;

public class TransferItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("size")
    private long size;

    @SerializedName("mimeType")
    private String mimeType;

    private String uriString;
    private long bytesTransferred;
    private TransferState state = TransferState.IDLE;
    private String localPath;

    public TransferItem() {}

    public TransferItem(String id, String name, long size, String mimeType, String uriString) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType != null ? mimeType : "application/octet-stream";
        this.uriString = uriString;
        this.bytesTransferred = 0;
        this.state = TransferState.IDLE;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getUriString() { return uriString; }
    public void setUriString(String uriString) { this.uriString = uriString; }

    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

    public TransferState getState() { return state; }
    public void setState(TransferState state) { this.state = state; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public int getProgressPercent() {
        if (size <= 0) return 0;
        return (int) Math.min(100, (bytesTransferred * 100L) / size);
    }

    public String getFormattedSize() {
        return formatBytes(size);
    }

    public String getFormattedTransferred() {
        return formatBytes(bytesTransferred);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
