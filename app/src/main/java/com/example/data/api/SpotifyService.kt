package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Moshi Models for Spotify API ---

data class SpotifyTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int
)

data class SpotifyImage(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?
)

data class SpotifyAlbum(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String,
    @Json(name = "images") val images: List<SpotifyImage>?
)

data class SpotifyArtistRef(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String
)

data class SpotifyTrack(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "duration_ms") val durationMs: Int,
    @Json(name = "preview_url") val previewUrl: String?,
    @Json(name = "artists") val artists: List<SpotifyArtistRef>,
    @Json(name = "album") val album: SpotifyAlbum,
    @Json(name = "popularity") val popularity: Int?
)

data class SpotifyTracksContainer(
    @Json(name = "items") val items: List<SpotifyTrack>
)

data class SpotifySearchResponse(
    @Json(name = "tracks") val tracks: SpotifyTracksContainer
)

data class SpotifyFollowers(
    @Json(name = "total") val total: Int
)

data class SpotifyArtist(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "images") val images: List<SpotifyImage>?,
    @Json(name = "followers") val followers: SpotifyFollowers?,
    @Json(name = "popularity") val popularity: Int?,
    @Json(name = "genres") val genres: List<String>?
)

data class SpotifyArtistTracksResponse(
    @Json(name = "tracks") val tracks: List<SpotifyTrack>
)

// --- Retrofit API Interfaces ---

interface SpotifyAccountsService {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun getAccessToken(
        @Header("Authorization") basicAuthHeader: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyTokenResponse
}

interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearerTokenHeader: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): SpotifySearchResponse

    @GET("v1/artists/{id}")
    suspend fun getArtist(
        @Header("Authorization") bearerTokenHeader: String,
        @Path("id") artistId: String
    ): SpotifyArtist

    @GET("v1/artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(
        @Header("Authorization") bearerTokenHeader: String,
        @Path("id") artistId: String,
        @Query("market") market: String = "US"
    ): SpotifyArtistTracksResponse

    @GET("v1/search")
    suspend fun searchArtists(
        @Header("Authorization") bearerTokenHeader: String,
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 5
    ): Map<String, Any> // We can parse dynamically or keep basic search
}

object SpotifyClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val accountsService: SpotifyAccountsService = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyAccountsService::class.java)

    val apiService: SpotifyApiService = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyApiService::class.java)
}
