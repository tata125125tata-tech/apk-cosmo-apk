package com.cosmogamestore.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.cosmogamestore.app.MainActivity
import com.cosmogamestore.app.R
import java.io.File
import java.util.Locale

/**
 * Helper class to manage dedicated Notification Channels and Custom Notifications
 * for APK downloads, progress tracking, and package installer triggers.
 */
object DownloadNotificationHelper {

    const val DOWNLOAD_CHANNEL_ID = "apk_download_channel"
    private const val DOWNLOAD_CHANNEL_NAME = "Game APK Downloads"
    private const val DOWNLOAD_CHANNEL_DESC = "Notifications for game downloads and installer status"

    const val ACTION_CANCEL_DOWNLOAD = "com.cosmogamestore.app.notification.ACTION_CANCEL_DOWNLOAD"
    const val ACTION_INSTALL_APK = "com.cosmogamestore.app.notification.ACTION_INSTALL_APK"
    const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    const val EXTRA_FILE_PATH = "extra_file_path"
    const val EXTRA_GAME_NAME = "extra_game_name"

    private var cachedLargeIcon: Bitmap? = null

    /**
     * Initializes the dedicated Notification Channel on Android 8.0 (API 26) and above.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                DOWNLOAD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance so progress updates don't repeatedly make sounds
            ).apply {
                description = DOWNLOAD_CHANNEL_DESC
                setShowBadge(true)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Returns a cached decoded bitmap for the large notification icon.
     */
    private fun getLargeIcon(context: Context): Bitmap? {
        if (cachedLargeIcon == null || cachedLargeIcon?.isRecycled == true) {
            try {
                cachedLargeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
            } catch (e: Exception) {
                // Fallback gracefully if decoding fails
            }
        }
        return cachedLargeIcon
    }

    /**
     * Displays or updates the active live download progress notification in the shade.
     */
    fun showProgressNotification(
        context: Context,
        downloadId: Long,
        gameName: String,
        progress: Int,
        bytesDownloaded: Long,
        totalBytes: Long
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val cleanProgress = progress.coerceIn(0, 100)
        val isIndeterminate = totalBytes <= 0L

        val subtext = if (!isIndeterminate) {
            "$cleanProgress% • ${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}"
        } else {
            "Downloading..."
        }

        // Tap notification to open main app
        val contentIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action PendingIntent
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            downloadId.toInt(),
            Intent(context, DownloadActionReceiver::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_GAME_NAME, gameName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_noti)
            .setContentTitle("Downloading $gameName")
            .setContentText(subtext)
            .setProgress(100, cleanProgress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification_cancel, "Cancel", cancelIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        getLargeIcon(context)?.let {
            builder.setLargeIcon(it)
        }

        try {
            notificationManager.notify(downloadId.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Android 13+ permission revoked
        }
    }

    /**
     * Updates notification when download reaches 100% with direct action to install the APK.
     */
    fun showCompleteNotification(
        context: Context,
        downloadId: Long,
        gameName: String,
        apkFile: File
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val installPendingIntent = createInstallPendingIntent(context, apkFile, downloadId)

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_noti)
            .setContentTitle(gameName)
            .setContentText("Download Complete - Tap to Install")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(installPendingIntent)
            .addAction(R.drawable.ic_notification_install, "Install Now", installPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        getLargeIcon(context)?.let {
            builder.setLargeIcon(it)
        }

        try {
            notificationManager.notify(downloadId.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Android 13+ permission revoked
        }
    }

    /**
     * Updates notification if download fails or is cancelled with an error.
     */
    fun showFailedNotification(
        context: Context,
        downloadId: Long,
        gameName: String,
        reason: String
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_noti)
            .setContentTitle("$gameName Download Failed")
            .setContentText(reason)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        getLargeIcon(context)?.let {
            builder.setLargeIcon(it)
        }

        try {
            notificationManager.notify(downloadId.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Ignore
        }
    }

    /**
     * Dismisses the notification for the given download ID.
     */
    fun cancelNotification(context: Context, downloadId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(downloadId.toInt())
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Creates a PendingIntent configured with FileProvider content URI to trigger PackageInstaller.
     */
    fun createInstallPendingIntent(context: Context, apkFile: File, requestCode: Long): PendingIntent {
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            context,
            requestCode.toInt(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Formats bytes into human-readable B, KB, MB, GB format.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(
            Locale.US,
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
}
