package com.razdrop.app.network.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
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
import com.razdrop.app.util.FileUtils;
import com.razdrop.app.util.PreferenceUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferServer {
    private static final String TAG = "RazDrop-Server";

    private final Context context;
    private final int port;
    private final String expectedPin;
    private TransferCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ServerSocket serverSocket;
    private Socket activeClientSocket;
    private Thread serverThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public TransferServer(Context context, int port, String expectedPin) {
        this.context = context.getApplicationContext();
        this.port = port > 0 ? port : RazPacket.DEFAULT_PORT;
        this.expectedPin = expectedPin;
    }

    public void setCallback(TransferCallback callback) {
        this.callback = callback;
    }

    public synchronized void start() {
        if (isRunning.get()) return;
        isRunning.set(true);
        isCancelled.set(false);

        serverThread = new Thread(this::listenLoop, "RazDrop-ServerThread");
        serverThread.start();
    }

    private void listenLoop() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            Log.d(TAG, "Server listening on port " + port);

            while (isRunning.get()) {
                try {
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "Accepted client connection from: " + client.getInetAddress());
                    synchronized (this) {
                        activeClientSocket = client;
                    }
                    handleClient(client);
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Log.w(TAG, "Client socket accept/handled with exception: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "Server socket fatal error on port " + port, e);
                postError("Server socket closed: " + e.getMessage(), e);
            }
        } finally {
            closeAll();
        }
    }

    private void handleClient(Socket socket) {
        isCancelled.set(false);
        DataInputStream in = null;
        DataOutputStream out = null;
        File currentTempFile = null;

        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setReceiveBufferSize(1048576);
            socket.setSendBufferSize(1048576);
            socket.setSoTimeout(60000); // 60s timeout

            in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 524288));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 524288));

            // Step 1: Read Handshake
            RazPacket.FrameHeader header = RazPacket.readHeader(in);
            if (header.type != PacketType.HANDSHAKE) {
                throw new IOException("Protocol error: expected HANDSHAKE, got " + header.type);
            }

            HandshakePacket clientHandshake = RazPacket.readJsonPayload(in, header.payloadLength, HandshakePacket.class);
            if (clientHandshake == null) {
                throw new IOException("Handshake payload is null");
            }

            boolean autoAccept = PreferenceUtils.isAutoAcceptEnabled(context);
            boolean pinMatches = autoAccept
                    || (expectedPin == null || expectedPin.isEmpty())
                    || (clientHandshake.getPin() != null && expectedPin.equals(clientHandshake.getPin()));
            PeerDevice peer = new PeerDevice(
                    socket.getInetAddress().getHostAddress(),
                    clientHandshake.getDeviceName(),
                    clientHandshake.getDeviceType(),
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort(),
                    ConnectionMode.LOCAL_LAN
            );

            postConnected(peer);

            // Step 2: Send Handshake ACK
            HandshakePacket ack = new HandshakePacket(
                    DeviceUtils.getDeviceName(context),
                    DeviceUtils.getDeviceType(context),
                    ""
            );
            ack.setAccepted(pinMatches);
            if (!pinMatches) {
                ack.setMessage("PIN mismatch. Please verify the TV pairing PIN.");
                RazPacket.writeJsonPacket(out, PacketType.HANDSHAKE_ACK, ack);
                throw new IOException("Authentication failed: invalid PIN");
            }
            RazPacket.writeJsonPacket(out, PacketType.HANDSHAKE_ACK, ack);

            // Step 3: Read Manifest
            header = RazPacket.readHeader(in);
            if (header.type != PacketType.TRANSFER_MANIFEST) {
                throw new IOException("Protocol error: expected TRANSFER_MANIFEST, got " + header.type);
            }

            ManifestPacket manifest = RazPacket.readJsonPayload(in, header.payloadLength, ManifestPacket.class);
            if (manifest == null || manifest.getItems() == null || manifest.getItems().isEmpty()) {
                throw new IOException("Manifest items are empty");
            }

            TransferBatch batch = new TransferBatch(
                    manifest.getBatchId(),
                    false, // incoming
                    peer,
                    manifest.getItems()
            );
            batch.setStartTimeMs(System.currentTimeMillis());
            batch.setOverallState(TransferState.TRANSFERRING);
            postManifest(batch);

            // Send Transfer Accept
            RazPacket.writeControlPacket(out, PacketType.TRANSFER_ACCEPT);

            // Step 4: Stream incoming files
            File receiveFolder = FileUtils.getReceiveFolder(context);
            long totalTransferredAllFiles = 0;
            long lastSpeedCalcTime = System.currentTimeMillis();
            long bytesSinceLastSpeedCalc = 0;

            for (int i = 0; i < batch.getItems().size(); i++) {
                if (isCancelled.get()) break;

                TransferItem item = batch.getItems().get(i);
                batch.setCurrentFileIndex(i);

                // Wait for FILE_START
                header = RazPacket.readHeader(in);
                if (header.type == PacketType.CANCEL) {
                    postCancelled();
                    return;
                }
                if (header.type != PacketType.FILE_START) {
                    throw new IOException("Expected FILE_START for item " + i + ", got " + header.type);
                }

                FileHeaderPacket fileHeader = RazPacket.readJsonPayload(in, header.payloadLength, FileHeaderPacket.class);
                if (fileHeader != null && fileHeader.getSize() > 0) {
                    item.setSize(fileHeader.getSize());
                }
                postFileStarted(item, i, batch.getItems().size());

                currentTempFile = FileUtils.createTempFile(receiveFolder, item.getId());
                long itemTransferred = 0;

                try (FileOutputStream fos = new FileOutputStream(currentTempFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos, 524288)) {

                    byte[] chunkBuf = new byte[262144];
                    boolean fileDone = false;

                    while (!fileDone) {
                        if (isCancelled.get()) {
                            postCancelled();
                            return;
                        }

                        header = RazPacket.readHeader(in);
                        if (header.type == PacketType.CANCEL) {
                            postCancelled();
                            return;
                        }
                        if (header.type == PacketType.FILE_END) {
                            fileDone = true;
                            break;
                        }
                        if (header.type != PacketType.FILE_DATA) {
                            throw new IOException("Expected FILE_DATA or FILE_END, got " + header.type);
                        }

                        int streamIndex = in.readInt();
                        int dataLength = header.payloadLength - 4;
                        if (dataLength < 0 || dataLength > 4194304) {
                            throw new IOException("Corrupt chunk size: " + dataLength);
                        }

                        int bytesReadThisChunk = 0;
                        while (bytesReadThisChunk < dataLength) {
                            int toRead = Math.min(chunkBuf.length, dataLength - bytesReadThisChunk);
                            int r = in.read(chunkBuf, 0, toRead);
                            if (r == -1) throw new IOException("Unexpected EOF during file streaming");
                            bos.write(chunkBuf, 0, r);
                            bytesReadThisChunk += r;
                            itemTransferred += r;
                            totalTransferredAllFiles += r;
                            bytesSinceLastSpeedCalc += r;
                        }

                        item.setBytesTransferred(itemTransferred);
                        batch.setTransferredBytes(totalTransferredAllFiles);

                        long now = System.currentTimeMillis();
                        long elapsedSinceCalc = now - lastSpeedCalcTime;
                        if (elapsedSinceCalc >= 250) {
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
                    bos.flush();
                }

                // File finished, rename to final name
                File targetFile = FileUtils.createTargetFile(receiveFolder, item.getName());
                if (currentTempFile.renameTo(targetFile)) {
                    item.setLocalPath(targetFile.getAbsolutePath());
                    FileUtils.scanMedia(context, targetFile);
                } else {
                    item.setLocalPath(currentTempFile.getAbsolutePath());
                }
                currentTempFile = null;

                item.setState(TransferState.COMPLETED);
                postFileCompleted(item, i);

                // Send File ACK
                RazPacket.writeControlPacket(out, PacketType.FILE_ACK);
            }

            if (!isCancelled.get()) {
                try {
                    header = RazPacket.readHeader(in);
                } catch (Exception ignored) {}

                batch.setOverallState(TransferState.COMPLETED);
                PreferenceUtils.saveToHistory(context, batch);
                postBatchCompleted(batch);
                RazPacket.writeControlPacket(out, PacketType.TRANSFER_COMPLETE);
            }
        } catch (Exception e) {
            if (!isCancelled.get() && isRunning.get()) {
                Log.e(TAG, "Error during transfer", e);
                postError("Transfer interrupted: " + e.getMessage(), e);
            }
        } finally {
            if (currentTempFile != null && currentTempFile.exists()) {
                currentTempFile.delete();
            }
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {}
            synchronized (this) {
                if (activeClientSocket == socket) {
                    activeClientSocket = null;
                }
            }
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public synchronized void cancel() {
        isCancelled.set(true);
        if (activeClientSocket != null) {
            try {
                DataOutputStream out = new DataOutputStream(activeClientSocket.getOutputStream());
                RazPacket.writeControlPacket(out, PacketType.CANCEL);
            } catch (Exception ignored) {}
            try {
                if (!activeClientSocket.isClosed()) {
                    activeClientSocket.close();
                }
            } catch (Exception ignored) {}
            activeClientSocket = null;
        }
        postCancelled();
    }

    public synchronized void stop() {
        isRunning.set(false);
        closeAll();
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
    }

    private void closeAll() {
        try {
            if (activeClientSocket != null && !activeClientSocket.isClosed()) {
                activeClientSocket.close();
            }
        } catch (Exception ignored) {}
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void postConnected(PeerDevice peer) {
        if (callback != null) mainHandler.post(() -> callback.onConnected(peer));
    }

    private void postManifest(TransferBatch batch) {
        if (callback != null) mainHandler.post(() -> callback.onManifestReceived(batch));
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
