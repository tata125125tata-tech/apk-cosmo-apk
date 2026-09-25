package com.cosmogamestore.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an APK game downloaded or downloading to the device.
 * Stores extracted metadata including package name, title, file path, size, date, download URL, and status.
 */
@Entity(tableName = "downloaded_games")
data class DownloadedGameEntity @JvmOverloads constructor(
    @PrimaryKey
    val packageName: String,
    val title: String,
    val filePath: String,
    val fileSize: Long,
    val downloadDate: Long,
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String = "",
    val status: String = STATUS_COMPLETED,
    val progress: Int = 100,
    val downloadId: Long = -1L
) {
    companion object {
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }

    val isDownloading: Boolean
        get() = status == STATUS_DOWNLOADING

    val isCompleted: Boolean
        get() = status == STATUS_COMPLETED

    val isFailed: Boolean
        get() = status == STATUS_FAILED
}
