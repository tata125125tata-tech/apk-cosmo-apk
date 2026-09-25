package com.cosmogamestore.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY id DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY id DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isDownloaded = 1 ORDER BY id DESC")
    fun getDownloadedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    fun getVideoFlowById(id: Long): Flow<VideoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET views = views + 1 WHERE id = :id")
    suspend fun incrementViewCount(id: Long)

    @Query("UPDATE videos SET likes = likes + 1, isLiked = 1 WHERE id = :id")
    suspend fun incrementLikeCount(id: Long)

    @Query("UPDATE videos SET isDownloading = :isDownloading, downloadProgress = :progress WHERE id = :id")
    suspend fun updateDownloadProgress(id: Long, isDownloading: Boolean, progress: Int)

    @Query("UPDATE videos SET isDownloaded = :isDownloaded, isDownloading = 0, offlinePath = :offlinePath, downloadProgress = 100, fileSize = :fileSize WHERE id = :id")
    suspend fun setDownloaded(id: Long, isDownloaded: Boolean, offlinePath: String?, fileSize: Long)

    @Query("UPDATE videos SET isDownloaded = 0, isDownloading = 0, offlinePath = NULL, downloadProgress = 0 WHERE id = :id")
    suspend fun clearOfflineStatus(id: Long)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)
}
