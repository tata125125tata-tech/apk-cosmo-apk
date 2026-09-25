package com.cosmogamestore.app.data.repository

import android.content.Context
import android.util.Log
import com.cosmogamestore.app.data.api.ApiClient
import com.cosmogamestore.app.data.api.CategoryDto
import com.cosmogamestore.app.data.api.VideoApiService
import com.cosmogamestore.app.data.api.VideoDto
import com.cosmogamestore.app.data.db.AppDatabase
import com.cosmogamestore.app.data.db.VideoDao
import com.cosmogamestore.app.data.db.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val videoDao: VideoDao,
    private val apiService: VideoApiService = ApiClient.videoApiService
) {
    companion object {
        private const val TAG = "VideoRepository"

        @Volatile
        private var INSTANCE: VideoRepository? = null

        fun getInstance(context: Context): VideoRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val instance = VideoRepository(db.videoDao())
                INSTANCE = instance
                instance
            }
        }
    }

    fun getVideosFlow(category: String?, searchQuery: String?): Flow<List<VideoEntity>> {
        val baseFlow = if (!category.isNullOrBlank() && !category.equals("All", ignoreCase = true)) {
            videoDao.getVideosByCategory(category)
        } else {
            videoDao.getAllVideos()
        }

        return baseFlow.map { list ->
            if (searchQuery.isNullOrBlank()) {
                list
            } else {
                val query = searchQuery.trim().lowercase()
                list.filter {
                    it.title.lowercase().contains(query) ||
                    it.category.lowercase().contains(query) ||
                    it.description.lowercase().contains(query)
                }
            }
        }
    }

    fun getDownloadedVideosFlow(): Flow<List<VideoEntity>> {
        return videoDao.getDownloadedVideos()
    }

    fun getVideoFlowById(id: Long): Flow<VideoEntity?> {
        return videoDao.getVideoFlowById(id)
    }

    suspend fun getVideoById(id: Long): VideoEntity? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)
    }

    suspend fun refreshVideos(category: String? = null, search: String? = null): Result<List<VideoEntity>> = withContext(Dispatchers.IO) {
        try {
            val apiCategory = if (!category.isNullOrBlank() && !category.equals("All", ignoreCase = true)) category else null
            val remoteDtos = apiService.getVideos(category = apiCategory, search = search)

            val entities = remoteDtos.map { dto ->
                val existing = videoDao.getVideoById(dto.id)
                VideoEntity(
                    id = dto.id,
                    title = dto.title,
                    description = dto.description ?: "",
                    coverUrl = dto.coverUrl,
                    videoUrl = dto.videoUrl,
                    category = dto.category,
                    views = if (existing != null && existing.views > dto.views) existing.views else dto.views,
                    likes = if (existing != null && existing.likes > dto.likes) existing.likes else dto.likes,
                    createdAt = dto.createdAt,
                    isLiked = existing?.isLiked ?: false,
                    isDownloaded = existing?.isDownloaded ?: false,
                    offlinePath = existing?.offlinePath,
                    downloadProgress = existing?.downloadProgress ?: 0,
                    isDownloading = existing?.isDownloading ?: false,
                    fileSize = existing?.fileSize ?: 0L
                )
            }

            if (entities.isNotEmpty()) {
                videoDao.insertVideos(entities)
            }
            Result.success(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh videos from Cloudflare Worker API", e)
            Result.failure(e)
        }
    }

    suspend fun getCategories(): Result<List<CategoryDto>> = withContext(Dispatchers.IO) {
        try {
            val categories = apiService.getCategories()
            Result.success(categories)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch categories from API", e)
            Result.failure(e)
        }
    }

    suspend fun incrementView(id: Long) = withContext(Dispatchers.IO) {
        try {
            videoDao.incrementViewCount(id)
            apiService.incrementView(id)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to increment view API for id $id", e)
        }
    }

    suspend fun incrementLike(id: Long) = withContext(Dispatchers.IO) {
        try {
            videoDao.incrementLikeCount(id)
            apiService.incrementLike(id)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to increment like API for id $id", e)
        }
    }

    suspend fun deleteOfflineVideo(video: VideoEntity) = withContext(Dispatchers.IO) {
        try {
            video.offlinePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            videoDao.clearOfflineStatus(video.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting offline video file", e)
        }
    }
}
