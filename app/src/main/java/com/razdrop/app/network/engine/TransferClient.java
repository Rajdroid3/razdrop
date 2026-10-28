package com.razdrop.app.network.engine;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.model.TransferState;
import com.razdrop.app.network.protocol.FileHeaderPacket;
import com.razdrop.app.network.protocol.HandshakePacket;
import com.razdrop.app.network.protocol.ManifestPacket;
import com.razdrop.app.network.protocol.PacketType;
import com.razdrop.app.network.protocol.RazPacket;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.PreferenceUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferClient {
    private static final String TAG = "RazDrop-Client";

    private final Context context;
    private final PeerDevice targetPeer;
    private final TransferBatch batch;
    private final String pin;
    private TransferCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Socket clientSocket;
    private Thread clientThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public TransferClient(Context context, PeerDevice targetPeer, TransferBatch batch, String pin) {
        this.context = context.getApplicationContext();
        this.targetPeer = targetPeer;
        this.batch = batch;
        this.pin = pin;
    }

    public void setCallback(TransferCallback callback) {
        this.callback = callback;
    }

    public synchronized void start() {
        if (isRunning.get()) return;
        isRunning.set(true);
        isCancelled.set(false);

        clientThread = new Thread(this::runTransfer, "RazDrop-ClientThread");
        clientThread.start();
    }

    private void runTransfer() {
        DataInputStream in = null;
        DataOutputStream out = null;

        try {
            clientSocket = new Socket();
            clientSocket.setTcpNoDelay(true);
            clientSocket.setKeepAlive(true);
            clientSocket.setReceiveBufferSize(1048576);
            clientSocket.setSendBufferSize(1048576);

            int port = targetPeer.getPort() > 0 ? targetPeer.getPort() : RazPacket.DEFAULT_PORT;
            Log.d(TAG, "Connecting to target peer " + targetPeer.getIpAddress() + ":" + port);

            clientSocket.connect(new InetSocketAddress(targetPeer.getIpAddress(), port), 15000);
            clientSocket.setSoTimeout(60000);

            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream(), 524288));
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream(), 524288));

            postConnected(targetPeer);

            // Step 1: Send Handshake
            HandshakePacket handshake = new HandshakePacket(
                    DeviceUtils.getDeviceName(context),
                    DeviceUtils.getDeviceType(context),
                    pin != null ? pin : targetPeer.getPinCode()
            );
            RazPacket.writeJsonPacket(out, PacketType.HANDSHAKE, handshake);

            // Step 2: Read Handshake ACK
            RazPacket.FrameHeader header = RazPacket.readHeader(in);
            if (header.type != PacketType.HANDSHAKE_ACK) {
                throw new IOException("Protocol error: expected HANDSHAKE_ACK, got " + header.type);
            }

            HandshakePacket ack = RazPacket.readJsonPayload(in, header.payloadLength, HandshakePacket.class);
            if (ack == null || !ack.isAccepted()) {
                String errorMsg = (ack != null && ack.getMessage() != null) ? ack.getMessage() : "Connection rejected by target device";
                throw new IOException(errorMsg);
            }

            // Step 3: Send Transfer Manifest
            ManifestPacket manifest = new ManifestPacket(
                    batch.getBatchId(),
                    batch.getTotalBytes(),
                    batch.getItems()
            );
            RazPacket.writeJsonPacket(out, PacketType.TRANSFER_MANIFEST, manifest);

            // Step 4: Wait for Transfer Accept
            header = RazPacket.readHeader(in);
            if (header.type == PacketType.CANCEL) {
                postCancelled();
                return;
            }
            if (header.type != PacketType.TRANSFER_ACCEPT) {
                throw new IOException("Expected TRANSFER_ACCEPT, got " + header.type);
            }
            if (header.payloadLength > 0) {
                in.skipBytes(header.payloadLength);
            }

            batch.setStartTimeMs(System.currentTimeMillis());
            batch.setOverallState(TransferState.TRANSFERRING);

            long totalTransferredAllFiles = 0;
            long lastSpeedCalcTime = System.currentTimeMillis();
            long bytesSinceLastSpeedCalc = 0;

            byte[] buffer = new byte[262144]; // 256 KB chunk size for high-throughput streaming
            long lastFlushTime = System.currentTimeMillis();

            for (int i = 0; i < batch.getItems().size(); i++) {
                if (isCancelled.get()) break;

                TransferItem item = batch.getItems().get(i);
                batch.setCurrentFileIndex(i);

                postFileStarted(item, i, batch.getItems().size());

                // Send FILE_START
                FileHeaderPacket fileHeader = new FileHeaderPacket(
                        i,
                        item.getId(),
                        item.getName(),
                        item.getSize(),
                        item.getMimeType()
                );
                RazPacket.writeJsonPacket(out, PacketType.FILE_START, fileHeader);

                // Open file input stream
                InputStream fileIn = null;
                try {
                    if (item.getUriString() != null) {
                        Uri uri = Uri.parse(item.getUriString());
                        try {
                            fileIn = context.getContentResolver().openInputStream(uri);
                        } catch (SecurityException se) {
                            Log.e(TAG, "Permission denied opening URI: " + item.getUriString(), se);
                            throw new IOException("Permission denied reading " + item.getName() + ". Please reselect the file.");
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to openInputStream, trying openAssetFileDescriptor: " + e.getMessage());
                        }

                        if (fileIn == null) {
                            try {
                                AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
                                if (afd != null) {
                                    fileIn = afd.createInputStream();
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed openAssetFileDescriptor: " + e.getMessage());
                            }
                        }
                    } else if (item.getLocalPath() != null) {
                        fileIn = new FileInputStream(new File(item.getLocalPath()));
                    }

                    if (fileIn == null) {
                        throw new IOException("Could not open input stream for: " + item.getName());
                    }

                    BufferedInputStream bis = new BufferedInputStream(fileIn, 524288);
                    int read;
                    long itemTransferred = 0;

                    while ((read = bis.read(buffer)) != -1) {
                        if (isCancelled.get()) {
                            postCancelled();
                            return;
                        }

                        RazPacket.writeRawChunk(out, i, buffer, 0, read);

                        itemTransferred += read;
                        totalTransferredAllFiles += read;
                        bytesSinceLastSpeedCalc += read;

                        item.setBytesTransferred(itemTransferred);
                        batch.setTransferredBytes(totalTransferredAllFiles);

                        long now = System.currentTimeMillis();
                        long elapsedSinceCalc = now - lastSpeedCalcTime;
                        if (elapsedSinceCalc >= 250) {
                            out.flush(); // Flush network stream periodically during UI update tick
                            double speed = (bytesSinceLastSpeedCalc * 1000.0) / elapsedSinceCalc;
                            batch.setSpeedBytesPerSec(speed);
                            long remainingBytes = Math.max(0, batch.getTotalBytes() - totalTransferredAllFiles);
                            long eta = speed > 0 ? (long) (remainingBytes / speed) : 0;
                            batch.setEtaSeconds(eta);

                            lastSpeedCalcTime = now;
                            bytesSinceLastSpeedCalc = 0;
                            postProgress(batch, item, totalTransferredAllFiles, batch.getTotalBytes(), speed, eta);
                        }
                    }
                    out.flush();
                    bis.close();
                } finally {
                    if (fileIn != null) {
                        try { fileIn.close(); } catch (Exception ignored) {}
                    }
                }

                // Explicitly send FILE_END
                RazPacket.writeControlPacket(out, PacketType.FILE_END);

                // Wait for FILE_ACK from receiver
                header = RazPacket.readHeader(in);
                if (header.type == PacketType.CANCEL) {
                    postCancelled();
                    return;
                }
                if (header.type != PacketType.FILE_ACK) {
                    throw new IOException("Expected FILE_ACK for item " + i + ", got " + header.type);
                }
                if (header.payloadLength > 0) {
                    in.skipBytes(header.payloadLength);
                }

                item.setState(TransferState.COMPLETED);
                postFileCompleted(item, i);
            }

            if (!isCancelled.get()) {
                RazPacket.writeControlPacket(out, PacketType.TRANSFER_COMPLETE);
                batch.setOverallState(TransferState.COMPLETED);
                PreferenceUtils.saveToHistory(context, batch);
                postBatchCompleted(batch);
            }

        } catch (Exception e) {
            if (!isCancelled.get() && isRunning.get()) {
                Log.e(TAG, "Client error during transfer", e);
                postError("Transfer interrupted: " + e.getMessage(), e);
            }
        } finally {
            closeSocket();
            isRunning.set(false);
        }
    }

    public synchronized void cancel() {
        isCancelled.set(true);
        if (clientSocket != null) {
            try {
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                RazPacket.writeControlPacket(out, PacketType.CANCEL);
            } catch (Exception ignored) {}
        }
        closeSocket();
        postCancelled();
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void postConnected(PeerDevice peer) {
        if (callback != null) mainHandler.post(() -> callback.onConnected(peer));
    }

    private void postFileStarted(TransferItem item, int index, int total) {
        if (callback != null) mainHandler.post(() -> callback.onFileStarted(item, index, total));
    }

    private void postProgress(TransferBatch batch, TransferItem item, long transferred, long total, double speed, long eta) {
        if (callback != null) mainHandler.post(() -> callback.onProgress(batch, item, transferred, total, speed, eta));
    }

    private void postFileCompleted(TransferItem item, int index) {
        if (callback != null) mainHandler.post(() -> callback.onFileCompleted(item, index));
    }

    private void postBatchCompleted(TransferBatch batch) {
        if (callback != null) mainHandler.post(() -> callback.onBatchCompleted(batch));
    }

    private void postError(String msg, Throwable t) {
        if (callback != null) mainHandler.post(() -> callback.onError(msg, t));
    }

    private void postCancelled() {
        if (callback != null) mainHandler.post(() -> callback.onCancelled());
    }
}
