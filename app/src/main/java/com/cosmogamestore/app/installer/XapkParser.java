package com.cosmogamestore.app.installer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.cosmogamestore.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Robust parser for .xapk archives containing manifest.json, split APKs, and optional OBB files.
 */
public class XapkParser {

    private static final String TAG = "XapkParser";

    public static PackageModel parse(Context context, File xapkFile) {
        if (xapkFile == null || !xapkFile.exists()) {
            return null;
        }

        PackageModel model = new PackageModel();
        model.setFile(xapkFile);
        model.setType(PackageModel.Type.XAPK);
        model.setSizeBytes(xapkFile.length());

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(xapkFile);

            String manifestJson = null;
            Drawable extractedIcon = null;
            int apkCount = 0;
            boolean hasObb = false;
            String baseApkEntryName = null;

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equalsIgnoreCase("manifest.json")) {
                    manifestJson = readZipEntryString(zipFile, entry);
                } else if (name.equalsIgnoreCase("icon.png")) {
                    Bitmap bmp = readZipEntryBitmap(zipFile, entry);
                    if (bmp != null) {
                        extractedIcon = new BitmapDrawable(context.getResources(), bmp);
                    }
                } else if (name.endsWith(".apk")) {
                    apkCount++;
                    if (baseApkEntryName == null || name.toLowerCase().contains("base") || !name.contains("config.")) {
                        baseApkEntryName = name;
                    }
                } else if (name.endsWith(".obb") || name.contains("/obb/")) {
                    hasObb = true;
                }
            }

            model.setSplitCount(apkCount);
            model.setHasObb(hasObb);

            // 1. Try parsing from manifest.json
            if (manifestJson != null) {
                try {
                    JSONObject json = new JSONObject(manifestJson);
                    if (json.has("package_name")) {
                        model.setPackageName(json.getString("package_name"));
                    }
                    if (json.has("name")) {
                        model.setTitle(json.getString("name"));
                    }
                    if (json.has("version_name")) {
                        model.setVersionName(json.getString("version_name"));
                    }
                    if (json.has("version_code")) {
                        model.setVersionCode(json.optLong("version_code", 1L));
                    }
                    if (json.has("expansions") && json.getJSONArray("expansions").length() > 0) {
                        model.setHasObb(true);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse manifest.json inside xapk", e);
                }
            }

            // 2. If manifest.json didn't provide complete info, inspect the base APK inside the zip
            if (model.getPackageName().isEmpty() && baseApkEntryName != null) {
                File tempApk = extractEntryToTemp(context, zipFile, baseApkEntryName);
                if (tempApk != null) {
                    try {
                        PackageManager pm = context.getPackageManager();
                        PackageInfo info = pm.getPackageArchiveInfo(tempApk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
                        if (info != null) {
                            model.setPackageName(info.packageName);
                            model.setVersionName(info.versionName);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                model.setVersionCode(info.getLongVersionCode());
                            } else {
                                model.setVersionCode(info.versionCode);
                            }
                            info.applicationInfo.sourceDir = tempApk.getAbsolutePath();
                            info.applicationInfo.publicSourceDir = tempApk.getAbsolutePath();
                            if (model.getTitle().isEmpty() || model.getTitle().equals("Unknown App")) {
                                CharSequence label = info.applicationInfo.loadLabel(pm);
                                if (label != null) {
                                    model.setTitle(label.toString());
                                }
                            }
                            if (extractedIcon == null) {
                                extractedIcon = info.applicationInfo.loadIcon(pm);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error inspecting internal base apk for xapk", e);
                    } finally {
                        tempApk.delete();
                    }
                }
            }

            // If title is still empty, derive from file name
            if (model.getTitle().isEmpty() || model.getTitle().equals("Unknown App")) {
                String fallbackTitle = xapkFile.getName().replace(".xapk", "").replace(".XAPK", "").replace("_", " ");
                model.setTitle(fallbackTitle);
            }

            if (extractedIcon != null) {
                model.setIcon(extractedIcon);
            } else {
                model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
            }

            // Check if installed on device
            checkInstalledStatus(context, model);

            return model;
        } catch (Exception e) {
            Log.e(TAG, "Error opening XAPK file: " + xapkFile.getAbsolutePath(), e);
            return null;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static String readZipEntryString(ZipFile zipFile, ZipEntry entry) {
        try (InputStream is = zipFile.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap readZipEntryBitmap(ZipFile zipFile, ZipEntry entry) {
        try (InputStream is = zipFile.getInputStream(entry)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            return null;
        }
    }

    private static File extractEntryToTemp(Context context, ZipFile zipFile, String entryName) {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) return null;

        File tempFile = new File(context.getCacheDir(), "temp_inspect_" + System.currentTimeMillis() + ".apk");
        try (InputStream is = zipFile.getInputStream(entry);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            return tempFile;
        } catch (Exception e) {
            if (tempFile.exists()) tempFile.delete();
            return null;
        }
    }

    private static void checkInstalledStatus(Context context, PackageModel model) {
        if (model.getPackageName().isEmpty()) return;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo installedInfo = pm.getPackageInfo(model.getPackageName(), 0);
            if (installedInfo != null) {
                model.setInstalled(true);
                model.setInstalledVersionName(installedInfo.versionName);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    model.setInstalledVersionCode(installedInfo.getLongVersionCode());
                } else {
                    model.setInstalledVersionCode(installedInfo.versionCode);
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            model.setInstalled(false);
        }
    }
}
