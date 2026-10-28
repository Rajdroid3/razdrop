package com.razdrop.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.widget.EditText;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FolderChooserHelper {

    public static final int REQUEST_CHOOSE_FOLDER_SAF = 4001;

    public interface OnFolderSelectedListener {
        void onFolderSelected(File folder);
    }

    public static void showFolderDialog(Context context, OnFolderSelectedListener listener) {
        List<File> options = FileUtils.getAvailableStorageDirectories(context);
        File currentFolder = FileUtils.getReceiveFolder(context);

        List<String> displayNames = new ArrayList<>();
        int selectedIndex = 0;

        for (int i = 0; i < options.size(); i++) {
            File f = options.get(i);
            String label = getDisplayLabelForFolder(context, f);
            if (f.getAbsolutePath().equals(currentFolder.getAbsolutePath())) {
                label += "  [Active]";
                selectedIndex = i;
            }
            displayNames.add(label);
        }

        int safIndex = displayNames.size();
        displayNames.add("📂 Browse via File Manager (Cx File Explorer / SAF)...");

        int customIndex = displayNames.size();
        displayNames.add("✏️ Custom Subfolder Name...");

        CharSequence[] items = displayNames.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Choose Save Destination");
        builder.setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            if (which < options.size()) {
                File chosen = options.get(which);
                saveAndNotify(context, chosen, listener);
            } else if (which == safIndex) {
                launchSafFolderPicker(context);
            } else if (which == customIndex) {
                showCustomFolderDialog(context, currentFolder, listener);
            }
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static String getDisplayLabelForFolder(Context context, File f) {
        if (f == null) return "Unknown";
        String path = f.getAbsolutePath();

        if (path.startsWith("/storage/") && !path.startsWith("/storage/emulated/")) {
            String remainder = path.substring("/storage/".length());
            String usbId = remainder;
            int slashIdx = remainder.indexOf('/');
            if (slashIdx > 0) {
                usbId = remainder.substring(0, slashIdx);
            }

            String desc = getVolumeDescription(context, usbId);
            String title = (desc != null && !desc.isEmpty()) ? desc + " (" + usbId + ")" : "USB Drive (" + usbId + ")";

            if (path.contains("/Android/data/")) {
                return "💾 " + title + " / App Storage";
            } else if (path.contains("/Download")) {
                return "💾 " + title + " / Download / " + f.getName();
            } else {
                return "💾 " + title + " / " + f.getName();
            }
        }

        if (path.contains("/Download")) return "📁 Downloads (Internal) / " + f.getName();
        if (path.contains("/Movies")) return "🎬 Movies (Internal) / " + f.getName();
        if (path.contains("/Pictures")) return "🖼️ Pictures (Internal) / " + f.getName();
        if (path.contains("/Music")) return "🎵 Music (Internal) / " + f.getName();
        if (path.equals(new File(Environment.getExternalStorageDirectory(), FileUtils.RAZDROP_FOLDER).getAbsolutePath())) {
            return "📱 Internal Storage / " + f.getName();
        }
        return "📁 " + f.getAbsolutePath();
    }

    public static String getVolumeDescription(Context context, String uuid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context != null && uuid != null) {
            try {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume vol : sm.getStorageVolumes()) {
                        if (uuid.equalsIgnoreCase(vol.getUuid())) {
                            return vol.getDescription(context);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void launchSafFolderPicker(Context context) {
        if (context instanceof Activity) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                ((Activity) context).startActivityForResult(intent, REQUEST_CHOOSE_FOLDER_SAF);
            } catch (Exception e) {
                Toast.makeText(context, "No compatible file manager found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void showCustomFolderDialog(Context context, File baseDir, OnFolderSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Custom Folder Name");

        final EditText input = new EditText(context);
        input.setHint("e.g. Movies2 or USB_Transfers");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        File parentDir = (baseDir != null && baseDir.getParentFile() != null)
                ? baseDir.getParentFile()
                : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");
                File customDir = new File(parentDir, folderName);
                saveAndNotify(context, customDir, listener);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void saveAndNotify(Context context, File folder, OnFolderSelectedListener listener) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!FileUtils.isDirectoryWritable(folder)) {
            String path = folder.getAbsolutePath();
            if (path.startsWith("/storage/") && !path.startsWith("/storage/emulated/")) {
                File[] extDirs = ContextCompat.getExternalFilesDirs(context, null);
                if (extDirs != null) {
                    for (File ed : extDirs) {
                        if (ed != null && !ed.getAbsolutePath().startsWith("/storage/emulated/")) {
                            File fallback = new File(ed, FileUtils.RAZDROP_FOLDER);
                            if (!fallback.exists()) fallback.mkdirs();
                            if (FileUtils.isDirectoryWritable(fallback)) {
                                folder = fallback;
                                break;
                            }
                        }
                    }
                }
            }
        }

        PreferenceUtils.setCustomSavePath(context, folder.getAbsolutePath());
        String display = getDisplayLabelForFolder(context, folder);
        Toast.makeText(context, "Save folder set to:\n" + display, Toast.LENGTH_LONG).show();
        if (listener != null) {
            listener.onFolderSelected(folder);
        }
    }

    public static void openInFileManager(Context context, File folder) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(folder.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Files saved in: " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Files saved in: " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }
}
