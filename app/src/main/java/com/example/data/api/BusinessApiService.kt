package com.example.data.api

import com.example.data.local.SongEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.UserEntity
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Moshi Models for Your Custom Business API ---

data class RemoteSongResponse(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String,
    @Json(name = "album") val album: String,
    @Json(name = "durationSec") val durationSec: Int,
    @Json(name = "streamUrl") val streamUrl: String,
    @Json(name = "coverUrl") val coverUrl: String,
    @Json(name = "genre") val genre: String,
    @Json(name = "isOfficialRelease") val isOfficialRelease: Boolean = true,
    @Json(name = "allowDownloads") val allowDownloads: Boolean = true
) {
    fun toLocalEntity(): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSec = durationSec,
            streamUrl = streamUrl,
            coverUrl = coverUrl,
            genre = genre,
            isOfficialRelease = isOfficialRelease,
            allowDownloads = allowDownloads,
            isDownloaded = false,
            localPath = null,
            isLiked = false,
            isPodcast = false,
            playCount = 0,
            status = "approved"
        )
    }
}

data class RemoteUserResponse(
    @Json(name = "userId") val userId: String,
    @Json(name = "email") val email: String,
    @Json(name = "name") val name: String,
    @Json(name = "photoUrl") val photoUrl: String,
    @Json(name = "isPremium") val isPremium: Boolean,
    @Json(name = "planName") val planName: String?
) {
    fun toLocalEntity(): UserEntity {
        return UserEntity(
            id = userId,
            email = email,
            name = name,
            photoUrl = photoUrl,
            isPremium = isPremium,
            planName = planName,
            followersCount = 0,
            followingCount = 0,
            createdAt = System.currentTimeMillis()
        )
    }
}

data class StandardApiResponse<T>(
    @Json(name = "status") val status: String, // "success" or "error"
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: T?
)

// --- Retrofit API Interface for Business Backend ---

interface BusinessApiService {

    // 1. Fetch available songs/catalogue from your cloud database
    @GET("api/v1/songs")
    suspend fun getSongsCatalog(
        @Header("Authorization") token: String? = null,
        @Query("genre") genre: String? = null
    ): StandardApiResponse<List<RemoteSongResponse>>

    // 2. Upload a new track (Business/Artist Dashboard feature)
    @POST("api/v1/songs/upload")
    suspend fun uploadNewSong(
        @Header("Authorization") token: String,
        @Body song: RemoteSongResponse
    ): StandardApiResponse<RemoteSongResponse>

    // 3. User Authentication (Login/Register)
    @POST("api/v1/auth/login")
    suspend fun authenticateUser(
        @Body credentials: Map<String, String>
    ): StandardApiResponse<RemoteUserResponse>

    // 4. Sync local user playlists to the cloud database
    @POST("api/v1/sync/playlists")
    suspend fun syncPlaylistsWithCloud(
        @Header("Authorization") token: String,
        @Body playlists: List<PlaylistEntity>
    ): StandardApiResponse<String>
}

// --- Business API Client Configuration ---

object BusinessApiClient {

    // IMPORTANT: Replace this base URL with your actual production backend server URL
    // e.g., "https://api.yourdomain.com/" or "https://your-firebase-functions-url.com/"
    var BASE_URL: String = "https://api.audiocitybusiness.example.com/"
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
            updateService()
        }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var _apiService: BusinessApiService? = null

    val apiService: BusinessApiService
        get() {
            if (_apiService == null) {
                updateService()
            }
            return _apiService!!
        }

    private fun updateService() {
        _apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BusinessApiService::class.java)
    }
}
