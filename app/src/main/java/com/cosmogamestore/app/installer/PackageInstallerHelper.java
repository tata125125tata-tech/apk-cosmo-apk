package com.cosmogamestore.app.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Core engine for installing standard APK and multi-split XAPK packages
 * using Android's native PackageInstaller API and FileProvider.
 */
public class PackageInstallerHelper {

    private static final String TAG = "PackageInstallerHelper";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface InstallCallback {
        void onProgress(String message, int percentage);
        void onSuccess();
        void onError(String error);
    }

    public static boolean canRequestPackageInstalls(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public static void promptUnknownSourcesPermission(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Install Permission Required")
                .setMessage("To install downloaded games and updates directly, please allow Cosmo Game Store to install unknown apps in system settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                            activity.startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void installPackage(Activity activity, PackageModel model, InstallCallback callback) {
        if (!canRequestPackageInstalls(activity)) {
            promptUnknownSourcesPermission(activity);
            if (callback != null) {
                callback.onError("Permission not granted to install unknown apps.");
            }
            return;
        }

        if (model == null || model.getFile() == null || !model.getFile().exists()) {
            if (callback != null) callback.onError("Package file not found on disk.");
            return;
        }

        if (model.getType() == PackageModel.Type.XAPK) {
            installXapk(activity, model.getFile(), model, callback);
        } else {
            installStandardApk(activity, model.getFile(), callback);
        }
    }

    public static void installStandardApk(Context context, File apkFile, InstallCallback callback) {
        try {
            if (callback != null) callback.onProgress("Launching Android Package Installer...", 50);

            Uri apkUri = FileProvider.getUriForFile(
                    context.getApplicationContext(),
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            if (callback != null) callback.onSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Error installing standard APK", e);
            if (callback != null) callback.onError("Failed to launch installer: " + e.getMessage());
            Toast.makeText(context, "Installer failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void installXapk(Context context, File xapkFile, PackageModel model, InstallCallback callback) {
        executor.execute(() -> {
            PackageInstaller.Session session = null;
            ZipFile zipFile = null;
            try {
                notifyProgress(callback, "Preparing XAPK installation session...", 10);

                PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                if (model != null && !model.getPackageName().isEmpty()) {
                    params.setAppPackageName(model.getPackageName());
                }
                if (xapkFile.length() > 0) {
                    params.setSize(xapkFile.length());
                }

                int sessionId = packageInstaller.createSession(params);
                session = packageInstaller.openSession(sessionId);

                zipFile = new ZipFile(xapkFile);
                int totalEntries = zipFile.size();
                int processed = 0;

                notifyProgress(callback, "Streaming split APKs to system installer...", 30);

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    processed++;

                    if (name.endsWith(".apk")) {
                        String cleanName = sanitizeZipEntryName(name);
                        long size = entry.getSize();
                        if (size <= 0) size = 0;

                        try (OutputStream out = session.openWrite(cleanName, 0, size);
                             InputStream in = zipFile.getInputStream(entry)) {
                            byte[] buffer = new byte[65536];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            session.fsync(out);
                        }
                    } else if (name.endsWith(".obb") || name.contains("/obb/")) {
                        notifyProgress(callback, "Extracting expansion game data (OBB)...", 70);
                        extractObb(context, zipFile, entry, model != null ? model.getPackageName() : null);
                    }

                    int pct = 30 + (int) (((float) processed / Math.max(1, totalEntries)) * 50);
                    notifyProgress(callback, "Writing: " + name, Math.min(85, pct));
                }

                notifyProgress(callback, "Committing installation to system...", 90);

                Intent statusIntent = new Intent(context, InstallStatusReceiver.class);
                statusIntent.setAction(InstallStatusReceiver.ACTION_INSTALL_STATUS);
                if (model != null && !model.getPackageName().isEmpty()) {
                    statusIntent.putExtra(InstallStatusReceiver.EXTRA_PACKAGE_NAME, model.getPackageName());
                }

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags);

                session.commit(pendingIntent.getIntentSender());
                session.close();
                session = null;

                notifyProgress(callback, "Installer prompt sent. Confirm on screen.", 100);
                notifySuccess(callback);

            } catch (Exception e) {
                Log.e(TAG, "Failed XAPK installation: " + xapkFile.getAbsolutePath(), e);
                if (session != null) {
                    try {
                        session.abandon();
                    } catch (Exception ignored) {}
                }
                notifyError(callback, "XAPK installation error: " + e.getMessage());
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private static void extractObb(Context context, ZipFile zipFile, ZipEntry entry, String packageName) {
        try {
            File obbBaseDir;
            if (packageName != null && !packageName.isEmpty()) {
                File externalStorage = Environment.getExternalStorageDirectory();
                obbBaseDir = new File(externalStorage, "Android/obb/" + packageName);
            } else {
                obbBaseDir = new File(context.getExternalFilesDir(null), "obb");
            }

            if (!obbBaseDir.exists()) {
                obbBaseDir.mkdirs();
            }

            String fileName = new File(entry.getName()).getName();
            File destObb = new File(obbBaseDir, fileName);

            try (InputStream in = zipFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(destObb)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            Log.d(TAG, "OBB extracted to: " + destObb.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Failed extracting OBB: " + entry.getName(), e);
        }
    }

    private static String sanitizeZipEntryName(String name) {
        String clean = new File(name).getName();
        return clean.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void notifyProgress(InstallCallback callback, String msg, int pct) {
        if (callback != null) {
            mainHandler.post(() -> callback.onProgress(msg, pct));
        }
    }

    private static void notifySuccess(InstallCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private static void notifyError(InstallCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }
}
