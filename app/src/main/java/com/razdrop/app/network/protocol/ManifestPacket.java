package com.razdrop.app.network.protocol;

import com.google.gson.annotations.SerializedName;
import com.razdrop.app.model.TransferItem;
import java.util.ArrayList;
import java.util.List;

public class ManifestPacket {
    @SerializedName("batchId")
    private String batchId;

    @SerializedName("totalBytes")
    private long totalBytes;

    @SerializedName("items")
    private List<TransferItem> items = new ArrayList<>();

    @SerializedName("accepted")
    private boolean accepted;

    public ManifestPacket() {}

    public ManifestPacket(String batchId, long totalBytes, List<TransferItem> items) {
        this.batchId = batchId;
        this.totalBytes = totalBytes;
        this.items = items != null ? items : new ArrayList<>();
        this.accepted = true;
    }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public List<TransferItem> getItems() { return items; }
    public void setItems(List<TransferItem> items) { this.items = items; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
