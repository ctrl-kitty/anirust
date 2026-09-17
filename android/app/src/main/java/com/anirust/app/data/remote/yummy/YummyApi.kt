package com.anirust.app.data.remote.yummy

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface YummyApi {

    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Header("Accept-Language") lang: String = "ru"
    ): YummyListResponse

    @GET("anime/{id}")
    suspend fun getAnimeDetail(
        @Path("id") id: Long,
        @Header("Accept-Language") lang: String = "ru"
    ): YummyDetailResponse

    @GET("anime/{id}/videos")
    suspend fun getAnimeVideos(
        @Path("id") id: Long,
        @Header("Accept-Language") lang: String = "ru"
    ): YummyVideosResponse
}
