package com.cosmogamestore.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.cosmogamestore.app.data.db.DownloadedGameEntity
import java.io.File

object ApkMetadataHelper {
    private const val TAG = "ApkMetadataHelper"

    data class ExtractedGame(
        val entity: DownloadedGameEntity,
        val icon: Drawable?
    )

    /**
     * Extracts full metadata and app icon from an uninstalled local .apk file
     * using Android's PackageManager.getPackageArchiveInfo.
     */
    fun extract(context: Context, apkFile: File): ExtractedGame? {
        if (!apkFile.exists() || !apkFile.canRead() || !apkFile.name.endsWith(".apk", ignoreCase = true)) {
            return null
        }

        val pm = context.packageManager
        val filePath = apkFile.absolutePath

        val packageInfo: PackageInfo? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(filePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(filePath, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting APK: $filePath", e)
            null
        }

        val appInfo = packageInfo?.applicationInfo
        var title = apkFile.nameWithoutExtension.replace(Regex("[-_]"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        var icon: Drawable? = null

        if (appInfo != null) {
            appInfo.sourceDir = filePath
            appInfo.publicSourceDir = filePath

            try {
                val label = appInfo.loadLabel(pm).toString()
                if (label.isNotBlank()) {
                    title = label
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load label for $filePath", e)
            }

            try {
                icon = appInfo.loadIcon(pm)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load icon for $filePath", e)
            }
        }

        val packageName = packageInfo?.packageName ?: "com.cosmo.game.${apkFile.nameWithoutExtension.lowercase()}"
        val versionName = packageInfo?.versionName ?: "1.0.0"
        val versionCode = if (packageInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } else {
            1L
        }

        val entity = DownloadedGameEntity(
            packageName = packageName,
            title = title,
            filePath = filePath,
            fileSize = apkFile.length(),
            downloadDate = apkFile.lastModified(),
            versionName = versionName,
            versionCode = versionCode
        )

        return ExtractedGame(entity, icon)
    }

    /**
     * Fast icon retrieval for an existing file
     */
    fun loadIcon(context: Context, apkFile: File): Drawable? {
        if (!apkFile.exists()) return null
        return try {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            packageInfo?.applicationInfo?.let {
                it.sourceDir = apkFile.absolutePath
                it.publicSourceDir = apkFile.absolutePath
                it.loadIcon(pm)
            }
        } catch (e: Exception) {
            null
        }
    }
}
