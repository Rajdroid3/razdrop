package com.razdrop.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.service.TransferService;
import com.razdrop.app.ui.adapters.FileItemAdapter;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.FileUtils;
import com.razdrop.app.util.NetworkUtils;
import com.razdrop.app.util.PreferenceUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FilePickerActivity extends Activity {
    private static final int REQUEST_PICK_FILES = 2001;
    public static final String EXTRA_SELECTED_ITEMS = "extra_selected_items";

    private RecyclerView rvFiles;
    private FileItemAdapter adapter;
    private TextView tvSummary;
    private Button btnConfirm;
    private final ArrayList<TransferItem> selectedItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        initViews();
        launchSafPicker("*/*");
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_picker_back);
        Button btnBrowseSaf = findViewById(R.id.btn_browse_saf);
        Button chipMedia = findViewById(R.id.chip_media);
        Button chipDocs = findViewById(R.id.chip_docs);
        Button chipApks = findViewById(R.id.chip_apks);
        Button chipDownloads = findViewById(R.id.chip_downloads);

        tvSummary = findViewById(R.id.tv_selected_summary);
        btnConfirm = findViewById(R.id.btn_confirm_send);
        rvFiles = findViewById(R.id.rv_picker_files);

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileItemAdapter();
        rvFiles.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnBrowseSaf.setOnClickListener(v -> launchSafPicker("*/*"));
        chipMedia.setOnClickListener(v -> launchSafPicker("image/*,video/*"));
        chipDocs.setOnClickListener(v -> launchSafPicker("application/pdf,application/msword,text/*"));
        chipApks.setOnClickListener(v -> launchSafPicker("application/vnd.android.package-archive"));
        chipDownloads.setOnClickListener(v -> launchSafPicker("*/*"));

        btnConfirm.setOnClickListener(v -> {
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, R.string.no_files_selected, Toast.LENGTH_SHORT).show();
                return;
            }

            if (DeviceUtils.isTelevision(this)) {
                showSendTargetDialog(selectedItems);
                return;
            }

            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_ITEMS, selectedItems);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ClipData clipData = null;
            for (TransferItem item : selectedItems) {
                if (item.getUriString() != null) {
                    Uri u = Uri.parse(item.getUriString());
                    if (clipData == null) {
                        clipData = ClipData.newRawUri("selected_files", u);
                    } else {
                        clipData.addItem(new ClipData.Item(u));
                    }
                }
            }
            if (clipData != null) {
                result.setClipData(clipData);
            }

            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void showSendTargetDialog(List<TransferItem> items) {
        List<String> options = new ArrayList<>();
        List<String> ipList = new ArrayList<>();

        String savedPhoneIp = PreferenceUtils.getLastPhoneIp(this);
        if (savedPhoneIp != null && !savedPhoneIp.isEmpty()) {
            options.add("📱 Phone (" + savedPhoneIp + ")");
            ipList.add(savedPhoneIp);
        }

        options.add("⌨️ Enter Phone IP Address...");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Send " + items.size() + " Files To:");
        builder.setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which < ipList.size()) {
                sendFilesDirectly(ipList.get(which), items);
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
            PreferenceUtils.setLastPhoneIp(this, ip);
            sendFilesDirectly(ip, items);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendFilesDirectly(String ip, List<TransferItem> items) {
        PeerDevice peer = new PeerDevice("phone", "Phone", DeviceType.PHONE, ip, 58920, ConnectionMode.LOCAL_LAN);
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
        serviceIntent.putExtra(TransferService.EXTRA_PIN, "");

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
        finish();
    }

    private void launchSafPicker(String mimeTypes) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType("*/*");

        if (mimeTypes != null && mimeTypes.contains(",")) {
            String[] split = mimeTypes.split(",");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, split);
        } else if (mimeTypes != null && !mimeTypes.equals("*/*")) {
            intent.setType(mimeTypes);
        }

        try {
            startActivityForResult(intent, REQUEST_PICK_FILES);
        } catch (Exception e) {
            try {
                Intent getContent = new Intent(Intent.ACTION_GET_CONTENT);
                getContent.setType(mimeTypes != null ? mimeTypes : "*/*");
                getContent.addCategory(Intent.CATEGORY_OPENABLE);
                getContent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(getContent, REQUEST_PICK_FILES);
            } catch (Exception ex) {
                showBuiltInStoragePicker();
            }
        }
    }

    private void showBuiltInStoragePicker() {
        List<File> searchDirs = FileUtils.getAvailableStorageDirectories(this);
        List<File> candidateFiles = new ArrayList<>();

        for (File dir : searchDirs) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] list = dir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile() && f.length() > 0 && !f.isHidden()) {
                            candidateFiles.add(f);
                        }
                    }
                }
            }
        }

        // Also check parent download dir
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dl != null && dl.exists()) {
            File[] list = dl.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile() && f.length() > 0 && !f.isHidden() && !candidateFiles.contains(f)) {
                        candidateFiles.add(f);
                    }
                }
            }
        }

        if (candidateFiles.isEmpty()) {
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Select Files to Send")
                    .setMessage("No files found in common folders (Downloads, Movies, Pictures, USB drives).\n\nPlease transfer files to this TV first, plug in a USB flash drive, or use an installed File Manager.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] fileNames = new String[candidateFiles.size()];
        boolean[] checked = new boolean[candidateFiles.size()];

        for (int i = 0; i < candidateFiles.size(); i++) {
            File f = candidateFiles.get(i);
            fileNames[i] = f.getName() + " (" + TransferItem.formatBytes(f.length()) + ")";
        }

        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Files to Send (" + candidateFiles.size() + " files)")
                .setMultiChoiceItems(fileNames, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("Select Files", (dialog, which) -> {
                    for (int i = 0; i < candidateFiles.size(); i++) {
                        if (checked[i]) {
                            File f = candidateFiles.get(i);
                            TransferItem item = new TransferItem(
                                    UUID.randomUUID().toString(),
                                    f.getName(),
                                    f.length(),
                                    FileUtils.getMimeType(this, null, f.getName()),
                                    null
                            );
                            item.setLocalPath(f.getAbsolutePath());
                            selectedItems.add(item);
                        }
                    }
                    updateSelectionUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILES && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    addUriItem(uri);
                }
            } else if (data.getData() != null) {
                addUriItem(data.getData());
            }
            updateSelectionUi();
        }
    }

    private void addUriItem(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        String name = FileUtils.getFileName(this, uri);
        long size = FileUtils.getFileSize(this, uri);
        String mime = FileUtils.getMimeType(this, uri, name);

        TransferItem item = new TransferItem(
                UUID.randomUUID().toString(),
                name,
                size,
                mime,
                uri.toString()
        );
        selectedItems.add(item);
    }

    private void updateSelectionUi() {
        adapter.updateItems(selectedItems);
        long totalBytes = 0;
        for (TransferItem item : selectedItems) {
            totalBytes += item.getSize();
        }

        if (selectedItems.isEmpty()) {
            tvSummary.setText(R.string.no_files_selected);
            btnConfirm.setEnabled(false);
            btnConfirm.setText(R.string.action_send);
        } else {
            String summary = selectedItems.size() + " files • " + TransferItem.formatBytes(totalBytes);
            tvSummary.setText(summary);
            btnConfirm.setEnabled(true);
            btnConfirm.setText("Send (" + selectedItems.size() + ")");
        }
    }
}
