package com.cosmogamestore.app.installer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.cosmogamestore.app.R;

import java.io.File;

/**
 * Parser for standard Android .apk files and installed applications.
 */
public class ApkParser {

    public static PackageModel parseApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            return null;
        }

        PackageModel model = new PackageModel();
        model.setFile(apkFile);
        model.setType(PackageModel.Type.APK);
        model.setSizeBytes(apkFile.length());
        model.setSplitCount(1);
        model.setHasObb(false);

        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            if (info != null) {
                model.setPackageName(info.packageName);
                model.setVersionName(info.versionName != null ? info.versionName : "1.0");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    model.setVersionCode(info.getLongVersionCode());
                } else {
                    model.setVersionCode(info.versionCode);
                }

                ApplicationInfo appInfo = info.applicationInfo;
                appInfo.sourceDir = apkFile.getAbsolutePath();
                appInfo.publicSourceDir = apkFile.getAbsolutePath();

                CharSequence label = appInfo.loadLabel(pm);
                if (label != null) {
                    model.setTitle(label.toString());
                } else {
                    model.setTitle(apkFile.getName().replace(".apk", "").replace(".APK", ""));
                }

                Drawable icon = appInfo.loadIcon(pm);
                if (icon != null) {
                    model.setIcon(icon);
                } else {
                    model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
                }
            } else {
                model.setTitle(apkFile.getName().replace(".apk", "").replace(".APK", ""));
                model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
            }
        } catch (Exception e) {
            model.setTitle(apkFile.getName().replace(".apk", "").replace(".APK", ""));
            model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
        }

        checkInstalledStatus(context, model);
        return model;
    }

    public static PackageModel parseInstalledApp(Context context, PackageInfo packageInfo) {
        if (packageInfo == null) return null;

        PackageModel model = new PackageModel();
        model.setType(PackageModel.Type.INSTALLED_APP);
        model.setPackageName(packageInfo.packageName);
        model.setVersionName(packageInfo.versionName != null ? packageInfo.versionName : "1.0");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            model.setVersionCode(packageInfo.getLongVersionCode());
        } else {
            model.setVersionCode(packageInfo.versionCode);
        }

        PackageManager pm = context.getPackageManager();
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        if (appInfo != null) {
            CharSequence label = appInfo.loadLabel(pm);
            model.setTitle(label != null ? label.toString() : packageInfo.packageName);
            try {
                Drawable icon = appInfo.loadIcon(pm);
                model.setIcon(icon != null ? icon : ContextCompat.getDrawable(context, R.drawable.ic_games));
            } catch (Exception e) {
                model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
            }

            if (appInfo.sourceDir != null) {
                File file = new File(appInfo.sourceDir);
                if (file.exists()) {
                    model.setFile(file);
                    model.setSizeBytes(file.length());
                }
            }
        } else {
            model.setTitle(packageInfo.packageName);
            model.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_games));
        }

        model.setInstalled(true);
        model.setInstalledVersionName(model.getVersionName());
        model.setInstalledVersionCode(model.getVersionCode());

        return model;
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
