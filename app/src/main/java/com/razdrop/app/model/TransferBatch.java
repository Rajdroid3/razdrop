package com.razdrop.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TransferBatch implements Serializable {
    private String batchId;
    private boolean isOutgoing;
    private PeerDevice peerDevice;
    private List<TransferItem> items = new ArrayList<>();
    private long totalBytes;
    private long transferredBytes;
    private int currentFileIndex;
    private long startTimeMs;
    private double speedBytesPerSec;
    private long etaSeconds;
    private TransferState overallState = TransferState.IDLE;
    private String errorMessage;

    public TransferBatch() {}

    public TransferBatch(String batchId, boolean isOutgoing, PeerDevice peerDevice, List<TransferItem> items) {
        this.batchId = batchId;
        this.isOutgoing = isOutgoing;
        this.peerDevice = peerDevice;
        if (items != null) {
            this.items = items;
            calculateTotalBytes();
        }
    }

    public void calculateTotalBytes() {
        totalBytes = 0;
        if (items != null) {
            for (TransferItem item : items) {
                totalBytes += item.getSize();
            }
        }
    }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public boolean isOutgoing() { return isOutgoing; }
    public void setOutgoing(boolean outgoing) { isOutgoing = outgoing; }

    public PeerDevice getPeerDevice() { return peerDevice; }
    public void setPeerDevice(PeerDevice peerDevice) { this.peerDevice = peerDevice; }

    public List<TransferItem> getItems() { return items; }
    public void setItems(List<TransferItem> items) {
        this.items = items;
        calculateTotalBytes();
    }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getTransferredBytes() { return transferredBytes; }
    public void setTransferredBytes(long transferredBytes) { this.transferredBytes = transferredBytes; }

    public int getCurrentFileIndex() { return currentFileIndex; }
    public void setCurrentFileIndex(int currentFileIndex) { this.currentFileIndex = currentFileIndex; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public double getSpeedBytesPerSec() { return speedBytesPerSec; }
    public void setSpeedBytesPerSec(double speedBytesPerSec) { this.speedBytesPerSec = speedBytesPerSec; }

    public long getEtaSeconds() { return etaSeconds; }
    public void setEtaSeconds(long etaSeconds) { this.etaSeconds = etaSeconds; }

    public TransferState getOverallState() { return overallState; }
    public void setOverallState(TransferState overallState) { this.overallState = overallState; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getOverallProgressPercent() {
        if (totalBytes <= 0) return 0;
        return (int) Math.min(100, (transferredBytes * 100L) / totalBytes);
    }

    public String getFormattedSpeed() {
        if (speedBytesPerSec < 1024) return String.format("%.0f B/s", speedBytesPerSec);
        if (speedBytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", speedBytesPerSec / 1024.0);
        return String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0));
    }

    public String getFormattedEta() {
        if (etaSeconds <= 0) return "--";
        if (etaSeconds < 60) return etaSeconds + "s";
        long minutes = etaSeconds / 60;
        long seconds = etaSeconds % 60;
        return String.format("%dm %02ds", minutes, seconds);
    }
}
