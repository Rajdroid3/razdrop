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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.razdrop.app.R;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.network.engine.TransferCallback;
import com.razdrop.app.service.TransferService;
import com.razdrop.app.ui.views.QrCodeView;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.FileUtils;
import com.razdrop.app.util.FolderChooserHelper;
import com.razdrop.app.util.NetworkUtils;
import com.razdrop.app.util.PreferenceUtils;
import com.razdrop.app.util.QrUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TvMainActivity extends Activity implements TransferCallback {
    private static final int REQUEST_TV_SEND_FILES = 4001;
    private static final int REQUEST_STORAGE_PERMS = 3001;

    private TextView tvTvName;
    private TextView tvTvIp;
    private QrCodeView qrView;
    private TextView tvQuickPin;
    private TextView tvStatusSubtitle;
    private TextView tvFolderPath;

    private String quickPin;
    private TransferService transferService;
    private boolean isBound = false;
    private PeerDevice lastConnectedPeer;
    private android.content.BroadcastReceiver screenshotReceiver;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TransferService.LocalBinder binder = (TransferService.LocalBinder) service;
            transferService = binder.getService();
            isBound = true;
            transferService.addListener(TvMainActivity.this);
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
        setContentView(R.layout.activity_tv_main);

        initViews();
        checkAndRequestPermissions();
        setupTvIdentity();
        startTvReceiverService();
        screenshotReceiver = com.razdrop.app.util.ScreenshotHelper.registerScreenshotReceiver(this);
    }

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_STORAGE_PERMS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupTvIdentity();
        startTvReceiverService();
        if (transferService != null) {
            transferService.ensureServerRunning();
        }
    }

    private void initViews() {
        tvTvName = findViewById(R.id.tv_tv_name);
        tvTvIp = findViewById(R.id.tv_tv_ip);
        qrView = findViewById(R.id.tv_qr_view);
        tvQuickPin = findViewById(R.id.tv_quick_pin);
        tvStatusSubtitle = findViewById(R.id.tv_status_subtitle);
        tvFolderPath = findViewById(R.id.tv_folder_path);

        LinearLayout tileReceive = findViewById(R.id.tile_tv_receive_status);
        LinearLayout tileSend = findViewById(R.id.tile_tv_send);
        LinearLayout tileOpenFolder = findViewById(R.id.tile_tv_open_folder);
        Button btnHistory = findViewById(R.id.btn_tv_history);
        Button btnSettings = findViewById(R.id.btn_tv_settings);

        tileSend.setOnClickListener(v -> {
            Intent intent = new Intent(this, FilePickerActivity.class);
            startActivityForResult(intent, REQUEST_TV_SEND_FILES);
        });

        tileOpenFolder.setOnClickListener(v -> {
            FolderChooserHelper.showFolderDialog(this, folder -> {
                tvFolderPath.setText(FolderChooserHelper.getDisplayLabelForFolder(this, folder));
            });
        });

        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Set initial D-pad focus on Receive tile
        tileReceive.requestFocus();
    }

    private void setupTvIdentity() {
        String name = DeviceUtils.getDeviceName(this);
        String ip = NetworkUtils.getLocalIpAddress();
        quickPin = DeviceUtils.generateQuickPin();

        tvTvName.setText(name);
        tvTvIp.setText(ip + ":58920");
        tvQuickPin.setText(quickPin.charAt(0) + " " + quickPin.charAt(1) + " " + quickPin.charAt(2) + " " + quickPin.charAt(3));

        File folder = FileUtils.getReceiveFolder(this);
        tvFolderPath.setText(FolderChooserHelper.getDisplayLabelForFolder(this, folder));

        // Generate QR code for phone camera scanner
        String qrContent = QrUtils.buildPairingUri(ip, 58920, quickPin, name);
        qrView.setQrContent(qrContent);
    }

    private void startTvReceiverService() {
        Intent serviceIntent = new Intent(this, TransferService.class);
        serviceIntent.setAction(TransferService.ACTION_START_SERVER);
        serviceIntent.putExtra(TransferService.EXTRA_PORT, 58920);
        serviceIntent.putExtra(TransferService.EXTRA_PIN, quickPin);
        startService(serviceIntent);

        bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    private void openFolder() {
        File folder = FileUtils.getReceiveFolder(this);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(folder.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Files saved in: " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Files saved in: " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnected(PeerDevice peer) {
        lastConnectedPeer = peer;
        if (peer != null && peer.getIpAddress() != null) {
            PreferenceUtils.setLastPhoneIp(this, peer.getIpAddress());
        }
        tvStatusSubtitle.setText("Connected to " + (peer != null ? peer.getName() : "device") + " — receiving files...");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TV_SEND_FILES && resultCode == RESULT_OK && data != null) {
            ArrayList<TransferItem> items = (ArrayList<TransferItem>) data.getSerializableExtra(FilePickerActivity.EXTRA_SELECTED_ITEMS);
            if (items != null && !items.isEmpty()) {
                showSendTargetDialog(items);
            } else {
                Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == FolderChooserHelper.REQUEST_CHOOSE_FOLDER_SAF && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}

                PreferenceUtils.setCustomSaveUri(this, treeUri.toString());
                String resolved = FileUtils.resolveTreeUriToPath(treeUri);
                if (resolved != null) {
                    File folder = new File(resolved);
                    if (!folder.exists()) folder.mkdirs();
                    PreferenceUtils.setCustomSavePath(this, folder.getAbsolutePath());
                    tvFolderPath.setText(FolderChooserHelper.getDisplayLabelForFolder(this, folder));
                    Toast.makeText(this, "Destination updated:\n" + FolderChooserHelper.getDisplayLabelForFolder(this, folder), Toast.LENGTH_LONG).show();
                } else {
                    tvFolderPath.setText(treeUri.getLastPathSegment());
                    Toast.makeText(this, "Destination updated via File Manager", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMS) {
            setupTvIdentity();
        }
    }

    private void showSendTargetDialog(List<TransferItem> items) {
        List<String> options = new ArrayList<>();
        List<PeerDevice> targets = new ArrayList<>();

        if (lastConnectedPeer != null && lastConnectedPeer.getIpAddress() != null) {
            options.add("📱 " + lastConnectedPeer.getName() + " (" + lastConnectedPeer.getIpAddress() + ")");
            targets.add(lastConnectedPeer);
        }

        String savedPhoneIp = PreferenceUtils.getLastPhoneIp(this);
        if (savedPhoneIp != null && !savedPhoneIp.isEmpty()) {
            boolean alreadyListed = lastConnectedPeer != null && savedPhoneIp.equals(lastConnectedPeer.getIpAddress());
            if (!alreadyListed) {
                PeerDevice p = new PeerDevice("phone", "Saved Phone", DeviceType.PHONE, savedPhoneIp, 58920, ConnectionMode.LOCAL_LAN);
                options.add("📱 Phone (" + savedPhoneIp + ")");
                targets.add(p);
            }
        }

        options.add("⌨️ Enter Phone IP Address...");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Send " + items.size() + " Files To:");
        builder.setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which < targets.size()) {
                startTransferToPeer(targets.get(which), items, "");
            } else {
                showEnterIpDialog(items);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showEnterIpDialog(List<TransferItem> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Enter Phone IP Address");

        final EditText input = new EditText(this);
        String localIp = NetworkUtils.getLocalIpAddress();
        String defaultPrefix = "192.168.0.";
        if (localIp != null && localIp.contains(".")) {
            defaultPrefix = localIp.substring(0, localIp.lastIndexOf('.') + 1);
        }
        String savedIp = PreferenceUtils.getLastPhoneIp(this);
        input.setText(savedIp != null ? savedIp : defaultPrefix);
        input.setTextColor(0xFFFFFFFF);
        input.setHint("e.g. " + defaultPrefix + "104");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Send Files", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter a valid IP", Toast.LENGTH_SHORT).show();
                return;
            }
            PeerDevice peer = new PeerDevice("phone", "Phone", DeviceType.PHONE, ip, 58920, ConnectionMode.LOCAL_LAN);
            PreferenceUtils.setLastPhoneIp(this, ip);
            startTransferToPeer(peer, items, "");
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startTransferToPeer(PeerDevice peer, List<TransferItem> items, String pin) {
        TransferBatch batch = new TransferBatch(
                UUID.randomUUID().toString(),
                true, // outgoing from TV
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

        startService(serviceIntent);

        Intent uiIntent = new Intent(this, TransferActivity.class);
        startActivity(uiIntent);
    }

    @Override
    public void onHandshakeReceived(PeerDevice peer, String pin, boolean autoAccept) {}

    @Override
    public void onManifestReceived(TransferBatch batch) {
        // Incoming files detected, launch TransferActivity on TV
        Intent intent = new Intent(this, TransferActivity.class);
        startActivity(intent);
    }

    @Override
    public void onFileStarted(TransferItem item, int index, int totalFiles) {}

    @Override
    public void onProgress(TransferBatch batch, TransferItem currentItem, long transferredBytes, long totalBytes, double speedBytesPerSec, long etaSeconds) {}

    @Override
    public void onFileCompleted(TransferItem item, int index) {}

    @Override
    public void onBatchCompleted(TransferBatch batch) {
        tvStatusSubtitle.setText("Last transfer completed (" + batch.getItems().size() + " files)");
    }

    @Override
    public void onError(String message, Throwable throwable) {
        tvStatusSubtitle.setText("Error: " + message);
    }

    @Override
    public void onCancelled() {
        tvStatusSubtitle.setText("Transfer cancelled");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
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
    }
}
