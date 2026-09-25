package com.cosmogamestore.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoDto(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = "",
    @Json(name = "cover_url") val coverUrl: String,
    @Json(name = "video_url") val videoUrl: String,
    @Json(name = "category") val category: String,
    @Json(name = "views") val views: Long = 0,
    @Json(name = "likes") val likes: Long = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class ApiResponse(
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "message") val message: String? = null
)
