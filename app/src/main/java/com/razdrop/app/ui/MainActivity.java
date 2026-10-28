package com.razdrop.app.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.network.ConnectionCoordinator;
import com.razdrop.app.network.engine.TransferCallback;
import com.razdrop.app.network.protocol.RazPacket;
import com.razdrop.app.service.TransferService;
import com.razdrop.app.ui.adapters.PeerAdapter;
import com.razdrop.app.ui.views.QrCodeView;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.NetworkUtils;
import com.razdrop.app.util.QrUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity implements ConnectionCoordinator.CoordinatorListener, TransferCallback {
    private static final int REQUEST_PICK_FILES_SEND = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private static final int REQUEST_SCAN_QR = 1003;

    private TextView tvDeviceInfo;
    private ProgressBar pbScanning;
    private LinearLayout layoutEmpty;
    private RecyclerView rvPeers;
    private Button btnScanQr;
    private Button btnPairPin;
    private Button btnToggleHotspot;

    private PeerAdapter peerAdapter;
    private ConnectionCoordinator coordinator;
    private ArrayList<TransferItem> pendingOutgoingItems;
    private PeerDevice preSelectedPeer;
    private String localPin;
    private TransferService transferService;
    private boolean isBound = false;
    private android.content.BroadcastReceiver screenshotReceiver;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TransferService.LocalBinder binder = (TransferService.LocalBinder) service;
            transferService = binder.getService();
            isBound = true;
            transferService.addListener(MainActivity.this);
            transferService.ensureServerRunning();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            transferService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If running on an Android TV or Google TV, launch the 10-foot TV experience directly
        if (DeviceUtils.isTelevision(this)) {
            startActivity(new Intent(this, TvMainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        checkAndRequestPermissions();
        setupCoordinator();
        screenshotReceiver = com.razdrop.app.util.ScreenshotHelper.registerScreenshotReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (coordinator != null) {
            startReceiverService();
        }
        if (transferService != null) {
            transferService.ensureServerRunning();
        }
    }

    private void initViews() {
        tvDeviceInfo = findViewById(R.id.tv_device_info);
        pbScanning = findViewById(R.id.pb_scanning);
        layoutEmpty = findViewById(R.id.layout_empty_peers);
        rvPeers = findViewById(R.id.rv_peers);
        btnScanQr = findViewById(R.id.btn_scan_qr);
        btnPairPin = findViewById(R.id.btn_pair_pin);
        btnToggleHotspot = findViewById(R.id.btn_toggle_hotspot);

        LinearLayout cardSend = findViewById(R.id.card_send);
        LinearLayout cardReceive = findViewById(R.id.card_receive);
        ImageButton btnHistory = findViewById(R.id.btn_history);
        ImageButton btnSettings = findViewById(R.id.btn_settings);

        rvPeers.setLayoutManager(new LinearLayoutManager(this));
        peerAdapter = new PeerAdapter(this::onPeerSelected);
        rvPeers.setAdapter(peerAdapter);

        cardSend.setOnClickListener(v -> {
            Intent intent = new Intent(this, FilePickerActivity.class);
            startActivityForResult(intent, REQUEST_PICK_FILES_SEND);
        });

        cardReceive.setOnClickListener(v -> showReceiveDialog());
        btnScanQr.setOnClickListener(v -> {
            Intent intent = new Intent(this, QrScanActivity.class);
            startActivityForResult(intent, REQUEST_SCAN_QR);
        });
        btnPairPin.setOnClickListener(v -> showManualPinDialog());
        btnToggleHotspot.setOnClickListener(v -> toggleHotspot());

        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        String name = DeviceUtils.getDeviceName(this);
        String ip = NetworkUtils.getLocalIpAddress();
        tvDeviceInfo.setText(name + " • " + ip);
    }

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void setupCoordinator() {
        localPin = DeviceUtils.generateQuickPin();
        coordinator = new ConnectionCoordinator(this);
        coordinator.setListener(this);
        coordinator.startDiscovery();
        coordinator.startAdvertising(localPin);
        startReceiverService();
    }

    private void startReceiverService() {
        Intent serviceIntent = new Intent(this, TransferService.class);
        serviceIntent.setAction(TransferService.ACTION_START_SERVER);
        serviceIntent.putExtra(TransferService.EXTRA_PORT, RazPacket.DEFAULT_PORT);
        serviceIntent.putExtra(TransferService.EXTRA_PIN, localPin);
        startService(serviceIntent);

        bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SCAN_QR && resultCode == RESULT_OK && data != null) {
            String ip = data.getStringExtra(QrScanActivity.EXTRA_SCANNED_IP);
            int port = data.getIntExtra(QrScanActivity.EXTRA_SCANNED_PORT, RazPacket.DEFAULT_PORT);
            String pin = data.getStringExtra(QrScanActivity.EXTRA_SCANNED_PIN);
            String name = data.getStringExtra(QrScanActivity.EXTRA_SCANNED_NAME);

            if (ip != null && !ip.isEmpty()) {
                PeerDevice scannedPeer = new PeerDevice(
                        ip + ":" + port,
                        name != null ? name : "Android TV",
                        DeviceType.TV,
                        ip,
                        port,
                        ConnectionMode.LOCAL_LAN
                );
                scannedPeer.setPinCode(pin);
                coordinator.addManualPeer(ip, port, name, pin);

                Toast.makeText(this, "Connected to " + scannedPeer.getName() + "!", Toast.LENGTH_SHORT).show();

                if (pendingOutgoingItems != null && !pendingOutgoingItems.isEmpty()) {
                    startTransferToPeer(scannedPeer, pendingOutgoingItems, pin);
                    pendingOutgoingItems = null;
                } else {
                    preSelectedPeer = scannedPeer;
                    Intent intent = new Intent(this, FilePickerActivity.class);
                    startActivityForResult(intent, REQUEST_PICK_FILES_SEND);
                }
            }
        } else if (requestCode == REQUEST_PICK_FILES_SEND && resultCode == RESULT_OK && data != null) {
            @SuppressWarnings("unchecked")
            ArrayList<TransferItem> items = (ArrayList<TransferItem>) data.getSerializableExtra(FilePickerActivity.EXTRA_SELECTED_ITEMS);
            if (items != null && !items.isEmpty()) {
                pendingOutgoingItems = items;

                if (preSelectedPeer != null) {
                    startTransferToPeer(preSelectedPeer, pendingOutgoingItems, preSelectedPeer.getPinCode());
                    preSelectedPeer = null;
                    pendingOutgoingItems = null;
                    return;
                }

                List<PeerDevice> peers = coordinator.getPeers();
                if (peers.size() == 1 && isValidIp(peers.get(0).getIpAddress())) {
                    startTransferToPeer(peers.get(0), pendingOutgoingItems, null);
                    pendingOutgoingItems = null;
                } else if (peers.isEmpty()) {
                    Toast.makeText(this, "Scan TV QR code to transmit files instantly", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, QrScanActivity.class);
                    startActivityForResult(intent, REQUEST_SCAN_QR);
                } else {
                    Toast.makeText(this, "Select a nearby device to start sending", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void onPeerSelected(PeerDevice peer) {
        if (!isValidIp(peer.getIpAddress())) {
            // Wi-Fi Direct peer needing P2P group connection first
            if (coordinator.getP2pManager().isSupported()) {
                Toast.makeText(this, "Connecting to " + peer.getName() + " via Wi-Fi Direct...", Toast.LENGTH_SHORT).show();
                coordinator.getP2pManager().connect(peer.getId());
            } else {
                Toast.makeText(this, "Please connect to " + peer.getName() + " via Scan QR", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (pendingOutgoingItems != null && !pendingOutgoingItems.isEmpty()) {
            startTransferToPeer(peer, pendingOutgoingItems, peer.getPinCode());
            pendingOutgoingItems = null;
        } else {
            preSelectedPeer = peer;
            Intent intent = new Intent(this, FilePickerActivity.class);
            startActivityForResult(intent, REQUEST_PICK_FILES_SEND);
        }
    }

    private void startTransferToPeer(PeerDevice peer, List<TransferItem> items, String pin) {
        if (peer == null || !isValidIp(peer.getIpAddress())) {
            Toast.makeText(this, "Invalid device IP address. Please scan TV QR code.", Toast.LENGTH_LONG).show();
            return;
        }

        TransferBatch batch = new TransferBatch(
                UUID.randomUUID().toString(),
                true,
                peer,
                items
        );

        Intent serviceIntent = new Intent(this, TransferService.class);
        serviceIntent.setAction(TransferService.ACTION_START_CLIENT);
        serviceIntent.putExtra(TransferService.EXTRA_PEER, peer);
        serviceIntent.putExtra(TransferService.EXTRA_BATCH, batch);
        serviceIntent.putExtra(TransferService.EXTRA_PIN, pin != null ? pin : peer.getPinCode());

        // Grant URI read permissions
        serviceIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (items != null && !items.isEmpty()) {
            ClipData clipData = null;
            for (TransferItem item : items) {
                if (item.getUriString() != null) {
                    Uri u = Uri.parse(item.getUriString());
                    if (clipData == null) {
                        clipData = ClipData.newRawUri("files", u);
                    } else {
                        clipData.addItem(new ClipData.Item(u));
                    }
                }
            }
            if (clipData != null) {
                serviceIntent.setClipData(clipData);
            }
        }

        startService(serviceIntent);

        Intent uiIntent = new Intent(this, TransferActivity.class);
        startActivity(uiIntent);
    }

    private void showReceiveDialog() {
        Intent serviceIntent = new Intent(this, TransferService.class);
        serviceIntent.setAction(TransferService.ACTION_START_SERVER);
        serviceIntent.putExtra(TransferService.EXTRA_PORT, RazPacket.DEFAULT_PORT);
        serviceIntent.putExtra(TransferService.EXTRA_PIN, localPin);
        startService(serviceIntent);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_receive, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvPin = dialogView.findViewById(R.id.tv_dialog_pin);
        TextView tvIp = dialogView.findViewById(R.id.tv_dialog_ip);
        QrCodeView qrView = dialogView.findViewById(R.id.dialog_qr_view);
        Button btnClose = dialogView.findViewById(R.id.btn_dialog_close);

        String ip = NetworkUtils.getLocalIpAddress();
        tvIp.setText(ip + ":" + RazPacket.DEFAULT_PORT);
        tvPin.setText(localPin);

        String qrContent = QrUtils.buildPairingUri(ip, RazPacket.DEFAULT_PORT, localPin, DeviceUtils.getDeviceName(this));
        qrView.setQrContent(qrContent);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showManualPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connect_pin, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etIp = dialogView.findViewById(R.id.et_target_ip);
        EditText etPin = dialogView.findViewById(R.id.et_target_pin);
        Button btnConnect = dialogView.findViewById(R.id.btn_dialog_connect);
        Button btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);

        // Pre-fill TV default IP if known on this network
        etIp.setText("192.168.0.167");

        btnConnect.setOnClickListener(v -> {
            String input = etIp.getText().toString().trim();
            String pin = etPin.getText().toString().trim();

            String ip = input;
            int port = RazPacket.DEFAULT_PORT;
            if (input.contains(":")) {
                String[] p = input.split(":");
                ip = p[0];
                try { port = Integer.parseInt(p[1]); } catch (Exception ignored) {}
            }

            if (!isValidIp(ip)) {
                Toast.makeText(this, "Please enter a valid IP (e.g. 192.168.0.167)", Toast.LENGTH_LONG).show();
                return;
            }

            PeerDevice manualPeer = new PeerDevice(
                    ip + ":" + port,
                    "Android TV (" + ip + ")",
                    DeviceType.TV,
                    ip,
                    port,
                    ConnectionMode.MANUAL_IP
            );
            manualPeer.setPinCode(pin);

            dialog.dismiss();

            if (pendingOutgoingItems != null && !pendingOutgoingItems.isEmpty()) {
                startTransferToPeer(manualPeer, pendingOutgoingItems, pin);
                pendingOutgoingItems = null;
            } else {
                coordinator.addManualPeer(ip, port, "Android TV", pin);
                Toast.makeText(this, "Connected! Now select files to send", Toast.LENGTH_SHORT).show();
                preSelectedPeer = manualPeer;
                Intent intent = new Intent(this, FilePickerActivity.class);
                startActivityForResult(intent, REQUEST_PICK_FILES_SEND);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void toggleHotspot() {
        if (coordinator.getHotspotManager().isActive()) {
            coordinator.stopHotspot();
            btnToggleHotspot.setText(R.string.mode_hotspot);
            btnToggleHotspot.setTextColor(getResources().getColor(R.color.raz_text_primary));
            Toast.makeText(this, "Local hotspot stopped", Toast.LENGTH_SHORT).show();
        } else {
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Transfer Without a Router")
                    .setMessage("You can transfer files directly offline without any Wi-Fi router or mobile data:\n\n"
                            + "1. Turn on your phone's Hotspot (or tap 'Start Direct Hotspot' below).\n"
                            + "2. On your TV/Projector, open Wi-Fi settings and connect to this phone.\n"
                            + "3. Open RazDrop on both devices and scan the TV QR code!\n\n"
                            + "Files transfer directly over local Wi-Fi at 50-100+ Mbps with zero internet used.")
                    .setPositiveButton("Start Direct Hotspot", (d, w) -> {
                        coordinator.startHotspotFallback();
                        btnToggleHotspot.setText("Hotspot Active");
                        btnToggleHotspot.setTextColor(getResources().getColor(R.color.raz_primary));
                        Toast.makeText(this, "Direct Hotspot active. Connect TV Wi-Fi to this phone.", Toast.LENGTH_LONG).show();
                    })
                    .setNeutralButton("Open Phone Hotspot Settings", (d, w) -> {
                        try {
                            Intent tetherIntent = new Intent(Intent.ACTION_MAIN);
                            tetherIntent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                            startActivity(tetherIntent);
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                            } catch (Exception ignored) {}
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onPeersChanged(List<PeerDevice> peers) {
        runOnUiThread(() -> {
            if (peers.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                rvPeers.setVisibility(View.GONE);
            } else {
                layoutEmpty.setVisibility(View.GONE);
                rvPeers.setVisibility(View.VISIBLE);
                peerAdapter.updatePeers(peers);
            }
        });
    }

    @Override
    public void onDirectConnectionReady(String hostAddress, boolean isHost) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Wi-Fi Direct connected to " + hostAddress, Toast.LENGTH_SHORT).show();
            if (pendingOutgoingItems != null && !pendingOutgoingItems.isEmpty()) {
                PeerDevice p2pPeer = new PeerDevice(
                        hostAddress + ":" + RazPacket.DEFAULT_PORT,
                        "Wi-Fi Direct Peer",
                        DeviceType.PHONE,
                        hostAddress,
                        RazPacket.DEFAULT_PORT,
                        ConnectionMode.WIFI_DIRECT
                );
                startTransferToPeer(p2pPeer, pendingOutgoingItems, null);
                pendingOutgoingItems = null;
            }
        });
    }

    @Override
    public void onHotspotReady(String ssid, String password, String hostIp) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Hotspot active: " + ssid, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onStatusMessage(String message) {
        runOnUiThread(() -> tvDeviceInfo.setText(DeviceUtils.getDeviceName(this) + " • " + message));
    }

    @Override
    public void onManifestReceived(TransferBatch batch) {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, TransferActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onConnected(PeerDevice peer) {}

    @Override
    public void onHandshakeReceived(PeerDevice peer, String pin, boolean autoAccept) {}

    @Override
    public void onFileStarted(TransferItem item, int index, int totalFiles) {}

    @Override
    public void onProgress(TransferBatch batch, TransferItem currentItem, long transferredBytes, long totalBytes, double speedBytesPerSec, long etaSeconds) {}

    @Override
    public void onFileCompleted(TransferItem item, int index) {}

    @Override
    public void onBatchCompleted(TransferBatch batch) {}

    @Override
    public void onError(String message, Throwable throwable) {}

    @Override
    public void onCancelled() {}

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (com.razdrop.app.util.ScreenshotHelper.handleKeyEvent(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenshotReceiver != null) {
            try {
                unregisterReceiver(screenshotReceiver);
            } catch (Exception ignored) {}
            screenshotReceiver = null;
        }
        if (isBound && transferService != null) {
            transferService.removeListener(this);
            unbindService(serviceConn);
            isBound = false;
        }
        if (coordinator != null) {
            coordinator.stopAll();
        }
    }
}
