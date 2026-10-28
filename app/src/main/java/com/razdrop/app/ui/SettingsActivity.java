package com.razdrop.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.razdrop.app.R;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.FileUtils;
import com.razdrop.app.util.FolderChooserHelper;
import com.razdrop.app.util.PreferenceUtils;
import java.io.File;

public class SettingsActivity extends Activity {
    private static final int REQUEST_TREE = 3001;

    private EditText etDeviceName;
    private Switch switchAutoAccept;
    private TextView tvFolderPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadSettings();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_settings_back);
        etDeviceName = findViewById(R.id.et_device_name);
        Button btnSaveName = findViewById(R.id.btn_save_name);
        switchAutoAccept = findViewById(R.id.switch_auto_accept);
        tvFolderPath = findViewById(R.id.tv_settings_folder_path);
        Button btnChooseFolder = findViewById(R.id.btn_choose_folder);

        btnBack.setOnClickListener(v -> finish());

        btnSaveName.setOnClickListener(v -> {
            String newName = etDeviceName.getText().toString().trim();
            if (!newName.isEmpty()) {
                PreferenceUtils.setDeviceName(this, newName);
                Toast.makeText(this, "Device name updated", Toast.LENGTH_SHORT).show();
            }
        });

        switchAutoAccept.setOnCheckedChangeListener((btn, isChecked) -> {
            PreferenceUtils.setAutoAcceptEnabled(this, isChecked);
        });

        btnChooseFolder.setOnClickListener(v -> {
            if (DeviceUtils.isTelevision(this)) {
                FolderChooserHelper.showFolderDialog(this, folder -> {
                    tvFolderPath.setText(folder.getAbsolutePath());
                });
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_TREE);
                } catch (Exception e) {
                    FolderChooserHelper.showFolderDialog(this, folder -> {
                        tvFolderPath.setText(folder.getAbsolutePath());
                    });
                }
            }
        });
    }

    private void loadSettings() {
        etDeviceName.setText(DeviceUtils.getDeviceName(this));
        switchAutoAccept.setChecked(PreferenceUtils.isAutoAcceptEnabled(this));

        File folder = FileUtils.getReceiveFolder(this);
        tvFolderPath.setText(folder.getAbsolutePath());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TREE && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    PreferenceUtils.setCustomSaveUri(this, treeUri.toString());
                    tvFolderPath.setText(treeUri.getLastPathSegment());
                    Toast.makeText(this, "Save destination updated", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not set folder permission", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
