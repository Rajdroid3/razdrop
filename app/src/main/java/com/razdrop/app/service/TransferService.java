package com.razdrop.app.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.razdrop.app.R;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.network.engine.TransferCallback;
import com.razdrop.app.network.engine.TransferClient;
import com.razdrop.app.network.engine.TransferServer;
import com.razdrop.app.ui.TransferActivity;
import java.util.ArrayList;
import java.util.List;

public class TransferService extends Service implements TransferCallback {
    private static final String TAG = "RazDrop-Service";
    private static final String CHANNEL_ID = "razdrop_transfers";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_SERVER = "com.razdrop.app.START_SERVER";
    public static final String ACTION_START_CLIENT = "com.razdrop.app.START_CLIENT";
    public static final String ACTION_CANCEL_TRANSFER = "com.razdrop.app.CANCEL_TRANSFER";

    public static final String EXTRA_BATCH = "extra_batch";
    public static final String EXTRA_PEER = "extra_peer";
    public static final String EXTRA_PIN = "extra_pin";
    public static final String EXTRA_PORT = "extra_port";

    private final IBinder binder = new LocalBinder();
    private final List<TransferCallback> listeners = new ArrayList<>();

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private NotificationManager notificationManager;

    private TransferServer activeServer;
    private TransferClient activeClient;
    private TransferBatch currentBatch;

    public class LocalBinder extends Binder {
        public TransferService getService() {
            return TransferService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        acquireLocks();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RazDrop:TransferWakeLock");
                wakeLock.acquire();
            }

            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RazDrop:WifiLock");
                wifiLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed acquiring wakelock / wifilock", e);
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CANCEL_TRANSFER.equals(action)) {
            cancelTransfer();
            stopForeground(false);
            ensureServerRunning();
            return START_STICKY;
        }

        startForegroundNotification("RazDrop Ready", "File transfer service active", -1);

        if (ACTION_START_SERVER.equals(action)) {
            int port = intent.getIntExtra(EXTRA_PORT, 58920);
            String pin = intent.getStringExtra(EXTRA_PIN);
            startServer(port, pin);
        } else if (ACTION_START_CLIENT.equals(action)) {
            PeerDevice peer = (PeerDevice) intent.getSerializableExtra(EXTRA_PEER);
            TransferBatch batch = (TransferBatch) intent.getSerializableExtra(EXTRA_BATCH);
            String pin = intent.getStringExtra(EXTRA_PIN);
            if (peer != null && batch != null) {
                startClient(peer, batch, pin);
            }
        }

        return START_STICKY;
    }

    private int currentServerPort = 58920;
    private String currentServerPin = "";

    private synchronized void startServer(int port, String pin) {
        this.currentServerPort = port > 0 ? port : 58920;
        this.currentServerPin = pin;
        if (activeServer != null && activeServer.isRunning()) {
            Log.d(TAG, "Server already running on port " + currentServerPort);
            return;
        }
        if (activeServer != null) {
            activeServer.stop();
            activeServer = null;
        }
        try {
            activeServer = new TransferServer(this, currentServerPort, currentServerPin);
            activeServer.setCallback(this);
            activeServer.start();
            Log.d(TAG, "TransferServer started on port " + currentServerPort);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TransferServer", e);
        }
    }

    public synchronized void ensureServerRunning() {
        if (activeServer == null || !activeServer.isRunning()) {
            Log.d(TAG, "ensureServerRunning: restarting server on port " + currentServerPort);
            startServer(currentServerPort, currentServerPin);
        }
    }

    private synchronized void startClient(PeerDevice peer, TransferBatch batch, String pin) {
        if (activeClient != null) {
            activeClient.cancel();
            activeClient = null;
        }
        this.currentBatch = batch;
        activeClient = new TransferClient(this, peer, batch, pin);
        activeClient.setCallback(this);
        activeClient.start();
    }

    public void cancelTransfer() {
        if (activeClient != null) {
            activeClient.cancel();
            activeClient = null;
        }
        if (activeServer != null) {
            activeServer.cancel();
        }
    }

    private void stopActiveTransfers() {
        if (activeServer != null) {
            activeServer.stop();
            activeServer = null;
        }
        if (activeClient != null) {
            activeClient.cancel();
            activeClient = null;
        }
    }

    private void startForegroundNotification(String title, String content, int progress) {
        Intent cancelIntent = new Intent(this, TransferService.class);
        cancelIntent.setAction(ACTION_CANCEL_TRANSFER);
        PendingIntent pCancel = PendingIntent.getService(this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, TransferActivity.class);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_send)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pOpen)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_close, getString(R.string.action_cancel), pCancel);

        if (progress >= 0) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                }
                startForeground(NOTIFICATION_ID, notification, type);
            } catch (Exception e) {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String title, String content, int progress) {
        if (notificationManager == null) return;
        Intent cancelIntent = new Intent(this, TransferService.class);
        cancelIntent.setAction(ACTION_CANCEL_TRANSFER);
        PendingIntent pCancel = PendingIntent.getService(this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, TransferActivity.class);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_send)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pOpen)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_close, getString(R.string.action_cancel), pCancel);

        if (progress >= 0) {
            builder.setProgress(100, progress, false);
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void addListener(TransferCallback listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }
    }

    public void removeListener(TransferCallback listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public TransferBatch getCurrentBatch() {
        return currentBatch;
    }

    @Override
    public void onConnected(PeerDevice peer) {
        updateNotification("RazDrop Connected", "Connected to " + peer.getName(), -1);
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onConnected(peer);
        }
    }

    @Override
    public void onHandshakeReceived(PeerDevice peer, String pin, boolean autoAccept) {
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onHandshakeReceived(peer, pin, autoAccept);
        }
    }

    @Override
    public void onManifestReceived(TransferBatch batch) {
        this.currentBatch = batch;
        updateNotification("Incoming Transfer", batch.getItems().size() + " files incoming", 0);
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onManifestReceived(batch);
        }
    }

    @Override
    public void onFileStarted(TransferItem item, int index, int totalFiles) {
        updateNotification("Transferring: " + item.getName(), (index + 1) + " of " + totalFiles, item.getProgressPercent());
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onFileStarted(item, index, totalFiles);
        }
    }

    @Override
    public void onProgress(TransferBatch batch, TransferItem currentItem, long transferredBytes, long totalBytes, double speedBytesPerSec, long etaSeconds) {
        this.currentBatch = batch;
        String content = currentItem.getName() + " (" + batch.getFormattedSpeed() + " • " + batch.getFormattedEta() + ")";
        updateNotification("Transferring Files", content, batch.getOverallProgressPercent());
        synchronized (listeners) {
            for (TransferCallback l : listeners) {
                l.onProgress(batch, currentItem, transferredBytes, totalBytes, speedBytesPerSec, etaSeconds);
            }
        }
    }

    @Override
    public void onFileCompleted(TransferItem item, int index) {
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onFileCompleted(item, index);
        }
    }

    @Override
    public void onBatchCompleted(TransferBatch batch) {
        updateNotification("Transfer Completed!", batch.getItems().size() + " files transferred successfully", 100);
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onBatchCompleted(batch);
        }
        stopForeground(false);
        ensureServerRunning();
    }

    @Override
    public void onError(String message, Throwable throwable) {
        updateNotification("Transfer Failed", message, 0);
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onError(message, throwable);
        }
        stopForeground(false);
        ensureServerRunning();
    }

    @Override
    public void onCancelled() {
        updateNotification("Transfer Cancelled", "Transfer was cancelled", 0);
        synchronized (listeners) {
            for (TransferCallback l : listeners) l.onCancelled();
        }
        stopForeground(false);
        ensureServerRunning();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopActiveTransfers();
        releaseLocks();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
