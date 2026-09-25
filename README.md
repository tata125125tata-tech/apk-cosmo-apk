# Cosmo Game Store

[![Build & Release Debug APK](https://github.com/tata125125tata-tech/cosmo-store-apk/actions/workflows/build-apk.yml/badge.svg)](https://github.com/tata125125tata-tech/cosmo-store-apk/actions/workflows/build-apk.yml)
[![GitHub Release](https://img.shields.io/github/v/release/tata125125tata-tech/cosmo-store-apk?color=blue&label=Latest%20Release)](https://github.com/tata125125tata-tech/cosmo-store-apk/releases)

An indie Android game store client featuring direct APK downloads, integrated game library, automatic installer, update tracking, and modern Clean Architecture.

---

## 📥 Direct APK Download

Whenever code is pushed to `main` or a release is tagged, GitHub Actions automatically compiles and publishes a new debug APK to **GitHub Releases**.

### ⚡ One-Click Download:
- **[👉 Download Latest CosmoGameStore-debug.apk](https://github.com/tata125125tata-tech/cosmo-store-apk/releases/latest/download/CosmoGameStore-debug.apk)** *(Direct APK download, no GitHub login required)*
- **[📦 View All Releases & Versions](https://github.com/tata125125tata-tech/cosmo-store-apk/releases)**
- **[🛠️ View GitHub Actions Artifacts](https://github.com/tata125125tata-tech/cosmo-store-apk/actions)**

---

## 🚀 How to Install on Android

1. Download the APK using the link above in your mobile browser (Chrome, Firefox, etc.).
2. When the download finishes, tap the notification or open your **Downloads** folder.
3. If Android prompts you with a security alert:
   - Tap **Settings**.
   - Enable **Allow from this source** for your browser or file manager.
4. Tap **Install** and launch the app!

---

## 🛠️ GitHub Actions Workflow (`.github/workflows/build-apk.yml`)

The automated CI/CD pipeline includes:
- **Java 17 setup** via Eclipse Temurin with Gradle dependency caching.
- **Gradle APK compilation** using `./gradlew assembleDebug`.
- **Integrity verification** checking that `app-debug.apk` is generated.
- **Workflow artifact archiving** keeping builds available for 30 days under Actions.
- **Automatic GitHub Releases** creating versioned releases (`v1.0.<build_number>`) with direct download assets:
  - `CosmoGameStore-debug.apk` (fixed latest link)
  - `CosmoGameStore-v1.0.<build_number>-debug.apk` (versioned file)

### GitHub Workflow Permissions (Required for Releases):
Ensure GitHub Actions has permission to publish releases:
1. In your GitHub repository, go to **Settings** → **Actions** → **General**.
2. Under **Workflow permissions**, select **Read and write permissions**.
3. Click **Save**.

### Manual Workflow Trigger:
You can trigger a fresh APK build on-demand at any time:
1. Go to **[Actions → Build & Release Debug APK](https://github.com/tata125125tata-tech/cosmo-store-apk/actions/workflows/build-apk.yml)**.
2. Click **Run workflow** → select branch (`main`) → **Run workflow**.

---

## 💻 Pushing from Local / Terminal

If you are cloning or pushing from your terminal:

```bash
# Initialize git if not already done
git init
git branch -M main

# Add remote
git remote add origin https://github.com/tata125125tata-tech/cosmo-store-apk.git

# Stage and commit
git add .
git commit -m "feat: complete Cosmo Game Store with CI/CD APK build workflow"

# Push to GitHub
git push -u origin main
```

---

## ⚙️ Tech Stack & Architecture
- **Language**: Kotlin & Java
- **Architecture**: Clean Architecture / MVVM
- **Database**: Room Database (SQLite)
- **UI**: Android Jetpack, Material Design 3, WebView Bridge
- **Download Management**: Android `DownloadManager` with real-time progress callbacks and notification controls
