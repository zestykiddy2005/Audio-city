package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- YouTube Data API v3 Moshi Models ---

data class YouTubeSearchId(
    @Json(name = "kind") val kind: String?,
    @Json(name = "videoId") val videoId: String?
)

data class YouTubeThumbnailDetail(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?
)

data class YouTubeThumbnails(
    @Json(name = "default") val default: YouTubeThumbnailDetail?,
    @Json(name = "medium") val medium: YouTubeThumbnailDetail?,
    @Json(name = "high") val high: YouTubeThumbnailDetail?
)

data class YouTubeSnippet(
    @Json(name = "publishedAt") val publishedAt: String?,
    @Json(name = "channelId") val channelId: String?,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "thumbnails") val thumbnails: YouTubeThumbnails?,
    @Json(name = "channelTitle") val channelTitle: String?
)

data class YouTubeSearchItem(
    @Json(name = "kind") val kind: String?,
    @Json(name = "id") val id: YouTubeSearchId,
    @Json(name = "snippet") val snippet: YouTubeSnippet
)

data class YouTubeSearchResponse(
    @Json(name = "items") val items: List<YouTubeSearchItem>?
)

// --- Retrofit API Service ---

interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

object YouTubeClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: YouTubeApiService = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeApiService::class.java)
}
