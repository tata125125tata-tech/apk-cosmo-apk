package com.cosmogamestore.app.notification

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * BroadcastReceiver to handle notification actions such as Cancel Download.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            DownloadNotificationHelper.ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(DownloadNotificationHelper.EXTRA_DOWNLOAD_ID, -1L)
                val gameName = intent.getStringExtra(DownloadNotificationHelper.EXTRA_GAME_NAME) ?: "Game"

                if (downloadId != -1L) {
                    Log.i("DownloadActionReceiver", "Cancelling download $downloadId for $gameName")
                    try {
                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.remove(downloadId)
                    } catch (e: Exception) {
                        Log.e("DownloadActionReceiver", "Error cancelling download", e)
                    }
                    DownloadNotificationHelper.cancelNotification(context, downloadId)
                    Toast.makeText(context, "Cancelled download: $gameName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
