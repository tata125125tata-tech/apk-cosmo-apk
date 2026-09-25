package com.cosmogamestore.app.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.Locale

/**
 * Production-ready helper for Android's system DownloadManager.
 * Handles queueing, status querying, progress calculation, and cancellation.
 */
class DownloadManagerHelper(private val context: Context) {

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    data class DownloadProgress(
        val downloadId: Long,
        val status: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int,
        val localUri: String?,
        val isSuccessful: Boolean,
        val isFailed: Boolean,
        val reason: Int
    )

    /**
     * Enqueue a download task in DownloadManager.
     *
     * @param url Download URL for the APK/file.
     * @param title Title displayed in notifications and library.
     * @param userAgent Optional custom user agent.
     * @param wifiOnly Whether to restrict downloading to Wi-Fi networks only.
     * @return Enqueued download ID, or -1 on failure.
     */
    fun enqueueDownload(
        url: String,
        title: String,
        userAgent: String? = null,
        wifiOnly: Boolean = false
    ): Pair<Long, File> {
        val fileName = guessApkFileName(url)
        val targetFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription("Downloading $fileName from Cosmo Game Store...")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            if (wifiOnly) {
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            } else {
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
            }

            if (!userAgent.isNullOrEmpty()) {
                addRequestHeader("User-Agent", userAgent)
            }

            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrEmpty()) {
                addRequestHeader("Cookie", cookie)
            }
        }

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue download for: $url", e)
            -1L
        }

        return Pair(downloadId, targetFile)
    }

    /**
     * Query current status and progress of an active download.
     */
    fun queryDownloadProgress(downloadId: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null
        try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val status = if (statusIdx != -1) cursor.getInt(statusIdx) else -1
                val downloadedBytes = if (downloadedIdx != -1) cursor.getLong(downloadedIdx) else 0L
                val totalBytes = if (totalIdx != -1) cursor.getLong(totalIdx) else -1L
                val localUri = if (uriIdx != -1) cursor.getString(uriIdx) else null
                val reason = if (reasonIdx != -1) cursor.getInt(reasonIdx) else 0

                val percent = if (totalBytes > 0) {
                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                return DownloadProgress(
                    downloadId = downloadId,
                    status = status,
                    bytesDownloaded = downloadedBytes,
                    totalBytes = totalBytes,
                    progressPercent = percent,
                    localUri = localUri,
                    isSuccessful = status == DownloadManager.STATUS_SUCCESSFUL,
                    isFailed = status == DownloadManager.STATUS_FAILED,
                    reason = reason
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying download progress for ID: $downloadId", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Emits download progress periodically as a Kotlin Flow until completed or failed.
     */
    fun pollProgressFlow(downloadId: Long, pollIntervalMs: Long = 500L): Flow<DownloadProgress> = flow {
        while (true) {
            val progress = queryDownloadProgress(downloadId)
            if (progress != null) {
                emit(progress)
                if (progress.isSuccessful || progress.isFailed) {
                    break
                }
            } else {
                break
            }
            delay(pollIntervalMs)
        }
    }

    /**
     * Cancel and remove download by ID.
     */
    fun cancelDownload(downloadId: Long): Boolean {
        return try {
            downloadManager.remove(downloadId) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download ID: $downloadId", e)
            false
        }
    }

    companion object {
        private const val TAG = "DownloadManagerHelper"

        fun guessApkFileName(url: String): String {
            var fileName = URLUtil.guessFileName(url, null, "application/vnd.android.package-archive")
            if (!fileName.lowercase(Locale.ROOT).endsWith(".apk")) {
                fileName = "$fileName.apk"
            }
            return fileName
        }
    }
}
