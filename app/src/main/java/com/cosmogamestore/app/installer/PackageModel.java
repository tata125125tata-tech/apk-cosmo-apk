package com.cosmogamestore.app.installer;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;

/**
 * Model representing a parsed package (.apk, .xapk, or installed application).
 */
public class PackageModel {

    public enum Type {
        APK,
        XAPK,
        INSTALLED_APP
    }

    private String title;
    private String packageName;
    private String versionName;
    private long versionCode;
    private long sizeBytes;
    private Drawable icon;
    private File file;
    private Uri uri;
    private Type type;
    private int splitCount;
    private boolean hasObb;
    private boolean isInstalled;
    private String installedVersionName;
    private long installedVersionCode;

    public PackageModel() {
        this.type = Type.APK;
        this.splitCount = 1;
        this.hasObb = false;
        this.isInstalled = false;
    }

    public String getTitle() {
        return title != null ? title : (packageName != null ? packageName : "Unknown App");
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPackageName() {
        return packageName != null ? packageName : "";
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersionName() {
        return versionName != null ? versionName : "1.0";
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(long versionCode) {
        this.versionCode = versionCode;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public void setSplitCount(int splitCount) {
        this.splitCount = splitCount;
    }

    public boolean hasObb() {
        return hasObb;
    }

    public void setHasObb(boolean hasObb) {
        this.hasObb = hasObb;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }

    public String getInstalledVersionName() {
        return installedVersionName;
    }

    public void setInstalledVersionName(String installedVersionName) {
        this.installedVersionName = installedVersionName;
    }

    public long getInstalledVersionCode() {
        return installedVersionCode;
    }

    public void setInstalledVersionCode(long installedVersionCode) {
        this.installedVersionCode = installedVersionCode;
    }

    public String getTypeDescription() {
        if (type == Type.XAPK) {
            StringBuilder sb = new StringBuilder("XAPK (Split APK");
            if (splitCount > 1) {
                sb.append(" x").append(splitCount);
            }
            if (hasObb) {
                sb.append(" + OBB");
            }
            sb.append(")");
            return sb.toString();
        } else if (type == Type.INSTALLED_APP) {
            return "Installed App";
        } else {
            return "Standard APK";
        }
    }
}
