# Cosmo Game Store

[![Build & Release Debug APK](https://github.com/tata125125tata-tech/apk-cosmo-apk/actions/workflows/build-apk.yml/badge.svg)](https://github.com/tata125125tata-tech/apk-cosmo-apk/actions/workflows/build-apk.yml)
[![GitHub Release](https://img.shields.io/github/v/release/tata125125tata-tech/apk-cosmo-apk?color=blue&label=Latest%20Release)](https://github.com/tata125125tata-tech/apk-cosmo-apk/releases)
[![Direct Download](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?logo=android&logoColor=white)](https://github.com/tata125125tata-tech/apk-cosmo-apk/releases/latest/download/CosmoGameStore-debug.apk)

A clean, native Android Material Design game store and package installer written in Java with traditional Android Views and XML layouts. Features robust `.apk` and `.xapk` (multi-split APK + OBB) parsing, PackageInstaller API integration, dual-view library management, app updates tracking, and customizable preferences.

---

## 📥 Direct APK Download

Whenever changes are pushed to `main` or a workflow is triggered, GitHub Actions automatically builds and publishes a debug APK directly to **GitHub Releases**.

### ⚡ One-Click Download:
- **[👉 Download Latest CosmoGameStore-debug.apk](https://github.com/tata125125tata-tech/apk-cosmo-apk/releases/latest/download/CosmoGameStore-debug.apk)** *(Direct APK file download, no login needed)*
- **[📦 View All Releases & Versions](https://github.com/tata125125tata-tech/apk-cosmo-apk/releases)**
- **[🛠️ View GitHub Actions Workflow Runs](https://github.com/tata125125tata-tech/apk-cosmo-apk/actions)**

---

## 📱 How to Install on Android

1. Tap **[Download Latest APK](https://github.com/tata125125tata-tech/apk-cosmo-apk/releases/latest/download/CosmoGameStore-debug.apk)** in your mobile browser.
2. When the download completes, tap the download notification or open the **Downloads** app.
3. If Android displays **"For your security, your phone is not allowed to install unknown apps from this source"**:
   - Tap **Settings**.
   - Enable **Allow from this source**.
4. Tap **Install** and open **Cosmo Game Store**!

---

## 📱 Four Native Tabs

1. **Browse (Game Store / Catalog)**:
   - Full-featured game store with responsive search, category filtering, and direct package downloading.
   - Built-in download manager with real-time status and notification controls.
2. **My Library (Owned Games & Packages)**:
   - **Downloaded Packages (APK / XAPK)**: Scans device storage for `.apk` and `.xapk` packages, displays app icons, version numbers, split package details, and one-tap install.
   - **Installed Games**: Lists installed applications on the device with one-tap Open and Uninstall actions.
   - **Pick File (SAF)**: Integrated with Android Storage Access Framework (`OpenDocument`) to pick and install any APK or XAPK from local storage or SD card.
3. **Updates (App Updates & Notifications)**:
   - Scans installed apps and checks for newer versions among downloaded packages and store repositories.
   - Categorized into "Updates Available" and "Up to Date".
   - One-tap "Check Now" scanning with security status alerts.
4. **Settings (App Preferences & Themes)**:
   - **Starting Page Selection**: Set the default launch tab to Browse, My Library, or Updates.
   - **Appearance**: Toggle Deep Space Dark Mode or Light theme with persistent `AppCompatDelegate` styling.
   - **Install Permissions**: Live check and shortcut to system Unknown Sources settings.
   - **Cache Management**: One-tap cache and temporary storage cleaner.

---

## 📦 App & XAPK Installer Module (`com.cosmogamestore.app.installer`)

- **XAPK Parser (`XapkParser.java`)**:
  - Unpacks and parses `manifest.json` from `.xapk` archives.
  - Extracts split configurations (base APK, architecture-specific splits like `arm64_v8a`, density splits).
  - Detects and extracts OBB expansion game data to `/Android/obb/<package_name>/`.
  - Extracts embedded `icon.png` or inspects internal base APKs for metadata.
- **APK Parser (`ApkParser.java`)**:
  - Parses standalone `.apk` files using Android `PackageManager`.
  - Extracts application labels, version codes, version names, and icons.
- **PackageInstaller Session (`PackageInstallerHelper.java`)**:
  - Creates atomic `PackageInstaller.Session` for split APK installations.
  - Streams all split APKs concurrently to the Android system installer.
  - Registers `InstallStatusReceiver` for seamless installation prompt callbacks.
- **Install Dialog Helper (`InstallDialogHelper.java`)**:
  - Clean Material 3 confirmation dialog showing app icon, title, version, size, type (APK vs XAPK splits), and progress bar.

---

## 🛠️ GitHub Actions Workflow (`.github/workflows/build-apk.yml`)

The automated workflow includes:
- **Java 21 (Temurin)** setup with Gradle dependency caching.
- **Automatic keystore management**: Restores `debug.keystore` or generates a valid debug keystore on the fly.
- **Debug APK compilation** using `./gradlew assembleDebug`.
- **Integrity & Checksum verification**: Calculates SHA-256 and outputs file size.
- **GitHub Artifacts**: Retains debug APKs under GitHub Actions for 30 days.
- **GitHub Releases**: Automatically creates new releases with permanent direct download links:
  - `CosmoGameStore-debug.apk` (permanent latest URL)
  - `CosmoGameStore-v1.0.<build_number>-debug.apk` (versioned file)

### Workflow Permissions Note:
To ensure GitHub Actions can publish releases to your repository:
1. In your GitHub repository, open **Settings** → **Actions** → **General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions**.
4. Click **Save**.

---

## 💻 Git Push & Synchronization

To push this codebase to your GitHub repository:

```bash
git add .
git commit -m "feat: complete native 4-tab Material Design app store with Java XAPK/APK installer"
git push -u origin main
```
