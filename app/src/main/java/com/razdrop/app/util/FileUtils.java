package com.razdrop.app.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileUtils {
    private static final String TAG = "RazDrop-Files";
    public static final String RAZDROP_FOLDER = "RazDrop";

    public static boolean isDirectoryWritable(File dir) {
        if (dir == null) return false;
        try {
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.exists()) return false;
            File testFile = new File(dir, ".raz_write_test_" + System.currentTimeMillis());
            if (testFile.createNewFile()) {
                testFile.delete();
                return true;
            }
            return dir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    public static File getReceiveFolder(Context context) {
        String customPath = PreferenceUtils.getCustomSavePath(context);
        if (customPath != null && !customPath.trim().isEmpty()) {
            try {
                File customDir = new File(customPath.trim());
                if (!customDir.exists()) {
                    customDir.mkdirs();
                }
                if (customDir.exists() && isDirectoryWritable(customDir)) {
                    return customDir;
                }
                // If it's a USB path that failed direct writing, attempt the app-specific USB fallback
                if (customPath.contains("/storage/") && !customPath.contains("/storage/emulated/")) {
                    File[] extDirs = ContextCompat.getExternalFilesDirs(context, null);
                    if (extDirs != null) {
                        for (File ed : extDirs) {
                            if (ed != null && !ed.getAbsolutePath().startsWith("/storage/emulated/")) {
                                File fallbackUsb = new File(ed, RAZDROP_FOLDER);
                                if (!fallbackUsb.exists()) fallbackUsb.mkdirs();
                                if (fallbackUsb.exists() && isDirectoryWritable(fallbackUsb)) {
                                    return fallbackUsb;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Custom save path invalid: " + customPath, e);
            }
        }

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File razdropDir = new File(downloads, RAZDROP_FOLDER);
        if (!razdropDir.exists()) {
            razdropDir.mkdirs();
        }
        if (isDirectoryWritable(razdropDir)) {
            return razdropDir;
        }

        // Fallback to app external files dir
        File extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (extFiles != null && !extFiles.exists()) {
            extFiles.mkdirs();
        }
        return extFiles != null ? extFiles : context.getFilesDir();
    }

    public static List<File> getAvailableStorageDirectories(Context context) {
        List<File> list = new ArrayList<>();
        Set<String> addedPaths = new HashSet<>();

        // 1. Primary Internal Storage Directories
        File downloads = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), RAZDROP_FOLDER);
        File movies = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), RAZDROP_FOLDER);
        File pictures = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), RAZDROP_FOLDER);
        File music = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RAZDROP_FOLDER);
        File internalRoot = new File(Environment.getExternalStorageDirectory(), RAZDROP_FOLDER);

        addDirectoryIfUnique(list, addedPaths, downloads);
        addDirectoryIfUnique(list, addedPaths, movies);
        addDirectoryIfUnique(list, addedPaths, pictures);
        addDirectoryIfUnique(list, addedPaths, music);
        addDirectoryIfUnique(list, addedPaths, internalRoot);

        // 2. StorageManager Removable Volumes (USB Drives / SD Cards)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    List<StorageVolume> volumes = sm.getStorageVolumes();
                    for (StorageVolume vol : volumes) {
                        if (vol.isRemovable()) {
                            File dir = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    dir = vol.getDirectory();
                                } catch (Throwable ignored) {}
                            }
                            if (dir == null) {
                                try {
                                    Method getPath = vol.getClass().getMethod("getPath");
                                    String p = (String) getPath.invoke(vol);
                                    if (p != null) dir = new File(p);
                                } catch (Throwable ignored) {}
                            }
                            if (dir == null) {
                                String uuid = vol.getUuid();
                                if (uuid != null) dir = new File("/storage/" + uuid);
                            }
                            if (dir != null && dir.exists()) {
                                addUsbDriveDirectories(context, list, addedPaths, dir);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "StorageManager detection error", e);
        }

        // 3. getExternalFilesDirs Removable Volumes
        try {
            File[] extDirs = ContextCompat.getExternalFilesDirs(context, null);
            if (extDirs != null) {
                for (File appExtDir : extDirs) {
                    if (appExtDir == null) continue;
                    String p = appExtDir.getAbsolutePath();
                    if (!p.startsWith("/storage/emulated/")) {
                        File usbFilesRazdrop = new File(appExtDir, RAZDROP_FOLDER);
                        addDirectoryIfUnique(list, addedPaths, usbFilesRazdrop);

                        int idx = p.indexOf("/Android/data/");
                        if (idx > 0) {
                            String rootPath = p.substring(0, idx);
                            File usbRoot = new File(rootPath);
                            if (usbRoot.exists()) {
                                addUsbDriveDirectories(context, list, addedPaths, usbRoot);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "getExternalFilesDirs detection error", e);
        }

        // 4. Scan /storage
        try {
            File storageDir = new File("/storage");
            if (storageDir.exists() && storageDir.isDirectory()) {
                File[] sub = storageDir.listFiles();
                if (sub != null) {
                    for (File drive : sub) {
                        String name = drive.getName();
                        if (!"emulated".equalsIgnoreCase(name) && !"self".equalsIgnoreCase(name) && !"knox".equalsIgnoreCase(name)) {
                            addUsbDriveDirectories(context, list, addedPaths, drive);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Scan /storage detection error", e);
        }

        return list;
    }

    private static void addUsbDriveDirectories(Context context, List<File> list, Set<String> addedPaths, File usbRoot) {
        if (usbRoot == null || !usbRoot.exists()) return;
        File directRazdrop = new File(usbRoot, RAZDROP_FOLDER);
        addDirectoryIfUnique(list, addedPaths, directRazdrop);

        File dlRazdrop = new File(new File(usbRoot, "Download"), RAZDROP_FOLDER);
        addDirectoryIfUnique(list, addedPaths, dlRazdrop);

        File appDir = new File(usbRoot, "Android/data/" + context.getPackageName() + "/files/" + RAZDROP_FOLDER);
        addDirectoryIfUnique(list, addedPaths, appDir);
    }

    private static void addDirectoryIfUnique(List<File> list, Set<String> addedPaths, File dir) {
        if (dir == null) return;
        String abs = dir.getAbsolutePath();
        if (!addedPaths.contains(abs)) {
            addedPaths.add(abs);
            list.add(dir);
        }
    }

    public static String resolveTreeUriToPath(Uri treeUri) {
        if (treeUri == null) return null;
        try {
            String path = treeUri.getPath();
            if (path == null) return null;
            int colonIdx = path.indexOf(':');
            String subPath = "";
            String volume = "";
            if (colonIdx != -1) {
                subPath = path.substring(colonIdx + 1);
                String beforeColon = path.substring(0, colonIdx);
                int lastSlash = beforeColon.lastIndexOf('/');
                volume = lastSlash != -1 ? beforeColon.substring(lastSlash + 1) : beforeColon;
            } else {
                return null;
            }

            if ("primary".equalsIgnoreCase(volume)) {
                return new File(Environment.getExternalStorageDirectory(), subPath).getAbsolutePath();
            } else {
                return new File("/storage/" + volume, subPath).getAbsolutePath();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resolving tree URI: " + treeUri, e);
            return null;
        }
    }

    public static File createTargetFile(File parentDir, String originalName) {
        String sanitized = originalName != null ? originalName.replaceAll("[\\\\/:*?\"<>|]", "_") : "file";
        File target = new File(parentDir, sanitized);
        if (!target.exists()) {
            return target;
        }

        // Handle duplicates: name (1).ext
        String baseName = sanitized;
        String extension = "";
        int dotIdx = sanitized.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = sanitized.substring(0, dotIdx);
            extension = sanitized.substring(dotIdx);
        }

        int count = 1;
        while (target.exists()) {
            target = new File(parentDir, baseName + " (" + count + ")" + extension);
            count++;
        }
        return target;
    }

    public static File createTempFile(File parentDir, String id) {
        return new File(parentDir, ".raz_tmp_" + (id != null ? id : System.currentTimeMillis()));
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        result = cursor.getString(nameIdx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error resolving filename from URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "unnamed_file";
    }

    public static long getFileSize(Context context, Uri uri) {
        long size = 0;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIdx >= 0) {
                        size = cursor.getLong(sizeIdx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error resolving filesize from URI", e);
            }
        }
        if (size <= 0) {
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    size = is.available();
                    is.close();
                }
            } catch (Exception ignored) {}
        }
        return size;
    }

    public static String getMimeType(Context context, Uri uri, String fileName) {
        String mime = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            mime = context.getContentResolver().getType(uri);
        }
        if (mime == null && fileName != null) {
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx >= 0) {
                String ext = fileName.substring(dotIdx + 1).toLowerCase();
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            }
        }
        return mime != null ? mime : "application/octet-stream";
    }

    public static void scanMedia(Context context, File file) {
        if (file == null || !file.exists()) return;
        try {
            MediaScannerConnection.scanFile(
                    context.getApplicationContext(),
                    new String[]{file.getAbsolutePath()},
                    null,
                    (path, uri) -> Log.d(TAG, "Media scanned: " + path + " -> " + uri)
            );
        } catch (Exception e) {
            Log.w(TAG, "Failed scanning media", e);
        }
    }
}
