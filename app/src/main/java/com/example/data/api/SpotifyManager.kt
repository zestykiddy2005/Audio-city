package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SpotifyManager {
    private const val TAG = "SpotifyManager"

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0

    // Retrieve configured Client ID and Client Secret from BuildConfig
    private val clientId: String
        get() = BuildConfig.SPOTIFY_CLIENT_ID

    private val clientSecret: String
        get() = BuildConfig.SPOTIFY_CLIENT_SECRET

    /**
     * Checks if Spotify credentials are valid and non-default.
     */
    fun isCredentialsConfigured(): Boolean {
        return clientId.isNotEmpty() && 
               clientId != "MY_SPOTIFY_CLIENT_ID" && 
               clientId != "SPOTIFY_CLIENT_ID_DEFAULT_VALUE" &&
               clientSecret.isNotEmpty() && 
               clientSecret != "MY_SPOTIFY_CLIENT_SECRET" && 
               clientSecret != "SPOTIFY_CLIENT_SECRET_DEFAULT_VALUE"
    }

    /**
     * Retrieves the current access token, refreshing it if expired or missing.
     */
    private suspend fun getOrFetchToken(): String? {
        if (!isCredentialsConfigured()) {
            Log.e(TAG, "Spotify credentials are not configured in Secrets panel!")
            return null
        }

        val currentTime = System.currentTimeMillis()
        if (accessToken != null && currentTime < tokenExpiresAt) {
            return accessToken
        }

        return withContext(Dispatchers.IO) {
            try {
                val rawString = "$clientId:$clientSecret"
                val base64Auth = Base64.encodeToString(rawString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val response = SpotifyClient.accountsService.getAccessToken(
                    basicAuthHeader = "Basic $base64Auth"
                )
                accessToken = response.accessToken
                // Buffer of 60 seconds
                tokenExpiresAt = System.currentTimeMillis() + (response.expiresIn * 1000) - 60000
                Log.d(TAG, "Successfully acquired Spotify Access Token. Expires in: ${response.expiresIn}s")
                accessToken
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Spotify Access Token", e)
                null
            }
        }
    }

    /**
     * Search for tracks on Spotify.
     */
    suspend fun searchTracks(query: String): List<SpotifyTrack> {
        val token = getOrFetchToken() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val response = SpotifyClient.apiService.searchTracks(
                    bearerTokenHeader = "Bearer $token",
                    query = query
                )
                response.tracks.items
            } catch (e: Exception) {
                Log.e(TAG, "Error searching Spotify tracks for query: $query", e)
                emptyList()
            }
        }
    }

    /**
     * Search for an artist on Spotify and return their first matching Artist ID.
     */
    suspend fun findArtistIdByName(name: String): String? {
        val token = getOrFetchToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val response = SpotifyClient.apiService.searchArtists(
                    bearerTokenHeader = "Bearer $token",
                    query = name
                )
                // Parse out the first artist ID from the map dynamically
                val artistsMap = response["artists"] as? Map<*, *>
                val items = artistsMap?.get("items") as? List<*>
                if (!items.isNullOrEmpty()) {
                    val firstArtist = items[0] as? Map<*, *>
                    firstArtist?.get("id") as? String
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up artist ID for name: $name", e)
                null
            }
        }
    }

    /**
     * Fetch real artist details using their Spotify Artist ID.
     */
    suspend fun getArtistDetails(artistId: String): SpotifyArtist? {
        val token = getOrFetchToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                SpotifyClient.apiService.getArtist(
                    bearerTokenHeader = "Bearer $token",
                    artistId = artistId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching details for Spotify Artist ID: $artistId", e)
                null
            }
        }
    }

    /**
     * Fetch real artist top tracks on Spotify.
     */
    suspend fun getArtistTopTracks(artistId: String): List<SpotifyTrack> {
        val token = getOrFetchToken() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val response = SpotifyClient.apiService.getArtistTopTracks(
                    bearerTokenHeader = "Bearer $token",
                    artistId = artistId
                )
                response.tracks
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching top tracks for Spotify Artist ID: $artistId", e)
                emptyList()
            }
        }
    }
}
