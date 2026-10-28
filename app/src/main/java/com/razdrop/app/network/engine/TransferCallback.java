package com.razdrop.app.network.engine;

import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;

public interface TransferCallback {
    void onConnected(PeerDevice peer);
    void onHandshakeReceived(PeerDevice peer, String pin, boolean autoAccept);
    void onManifestReceived(TransferBatch batch);
    void onFileStarted(TransferItem item, int index, int totalFiles);
    void onProgress(TransferBatch batch, TransferItem currentItem, long transferredBytes, long totalBytes, double speedBytesPerSec, long etaSeconds);
    void onFileCompleted(TransferItem item, int index);
    void onBatchCompleted(TransferBatch batch);
    void onError(String message, Throwable throwable);
    void onCancelled();
}
