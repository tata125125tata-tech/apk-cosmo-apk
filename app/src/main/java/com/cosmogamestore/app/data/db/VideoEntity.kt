package com.cosmogamestore.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String = "",
    val coverUrl: String,
    val videoUrl: String,
    val category: String,
    val views: Long = 0,
    val likes: Long = 0,
    val createdAt: String? = null,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val offlinePath: String? = null,
    val downloadProgress: Int = 0,
    val isDownloading: Boolean = false,
    val fileSize: Long = 0L
)
