package com.cosmogamestore.app.core.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cosmogamestore.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class VideoDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_VIDEO_ID = "key_video_id"
        const val KEY_VIDEO_URL = "key_video_url"
        const val KEY_VIDEO_TITLE = "key_video_title"
        private const val TAG = "VideoDownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getLong(KEY_VIDEO_ID, -1L)
        val videoUrl = inputData.getString(KEY_VIDEO_URL)
        val videoTitle = inputData.getString(KEY_VIDEO_TITLE) ?: "video_$videoId"

        if (videoId == -1L || videoUrl.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val videoDao = db.videoDao()

        try {
            videoDao.updateDownloadProgress(videoId, isDownloading = true, progress = 0)

            val dir = File(applicationContext.getExternalFilesDir(null), "downloaded_videos")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sanitizedTitle = videoTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val destinationFile = File(dir, "video_${videoId}_${sanitizedTitle}.mp4")

            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(videoUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                videoDao.updateDownloadProgress(videoId, isDownloading = false, progress = 0)
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                videoDao.updateDownloadProgress(videoId, isDownloading = false, progress = 0)
                return@withContext Result.failure()
            }

            val contentLength = body.contentLength()
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastReportedProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress != lastReportedProgress) {
                            lastReportedProgress = progress
                            videoDao.updateDownloadProgress(videoId, isDownloading = true, progress = progress)
                            setProgress(workDataOf("progress" to progress))
                        }
                    }
                }

                outputStream.flush()
                val finalSize = destinationFile.length()
                videoDao.setDownloaded(
                    id = videoId,
                    isDownloaded = true,
                    offlinePath = destinationFile.absolutePath,
                    fileSize = finalSize
                )
                Log.d(TAG, "Video $videoId downloaded successfully to ${destinationFile.absolutePath}")
                Result.success(workDataOf("path" to destinationFile.absolutePath))
            } catch (e: Exception) {
                Log.e(TAG, "Error while streaming video download for id $videoId", e)
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                videoDao.updateDownloadProgress(videoId, isDownloading = false, progress = 0)
                Result.failure()
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed video download worker for id $videoId", e)
            videoDao.updateDownloadProgress(videoId, isDownloading = false, progress = 0)
            Result.failure()
        }
    }
}
