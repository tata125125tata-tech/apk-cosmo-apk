package com.cosmogamestore.app.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VideoApiService {

    @GET("videos")
    suspend fun getVideos(
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): List<VideoDto>

    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    @POST("videos/view/{id}")
    suspend fun incrementView(
        @Path("id") id: Long
    ): ApiResponse

    @POST("videos/like/{id}")
    suspend fun incrementLike(
        @Path("id") id: Long
    ): ApiResponse
}
