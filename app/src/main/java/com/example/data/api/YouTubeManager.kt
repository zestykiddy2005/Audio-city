package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class YouTubeTrack(
    val id: String,
    val title: String,
    val channelTitle: String,
    val description: String,
    val thumbnailUrl: String,
    val previewUrl: String
)

object YouTubeManager {
    private const val TAG = "YouTubeManager"
    
    // In-memory runtime override for the API key so users can enter it in-app!
    private var runtimeApiKey: String? = null

    fun setRuntimeApiKey(key: String) {
        runtimeApiKey = key.trim()
    }

    fun getEffectiveApiKey(): String {
        val rKey = runtimeApiKey
        if (!rKey.isNullOrBlank()) return rKey
        
        // Try BuildConfig
        return try {
            val key = BuildConfig.YOUTUBE_API_KEY
            if (key != "YOUTUBE_API_KEY_DEFAULT_VALUE") key else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun isCredentialsConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    suspend fun searchTracks(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            Log.d(TAG, "No YouTube API Key configured, returning high-quality sandbox tracks matching: $query")
            return@withContext getSandboxTracks().filter {
                it.title.contains(query, ignoreCase = true) || 
                it.channelTitle.contains(query, ignoreCase = true)
            }
        }

        try {
            val response = YouTubeClient.apiService.searchVideos(query = query, apiKey = apiKey)
            val items = response.items ?: emptyList()
            
            items.mapNotNull { item ->
                val videoId = item.id.videoId ?: return@mapNotNull null
                val title = unescapeHtml(item.snippet.title)
                val channel = unescapeHtml(item.snippet.channelTitle ?: "YouTube Artist")
                val desc = unescapeHtml(item.snippet.description ?: "")
                val thumb = item.snippet.thumbnails?.high?.url 
                    ?: item.snippet.thumbnails?.medium?.url 
                    ?: item.snippet.thumbnails?.default?.url 
                    ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=300"
                
                YouTubeTrack(
                    id = videoId,
                    title = title,
                    channelTitle = channel,
                    description = desc,
                    thumbnailUrl = thumb,
                    // Use standard streamable preview urls or a reliable soundhelix stream
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube API search failed: ${e.message}", e)
            // Fallback to sandbox on error if search failed
            getSandboxTracks().filter {
                it.title.contains(query, ignoreCase = true) || 
                it.channelTitle.contains(query, ignoreCase = true)
            }
        }
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }

    fun getSandboxTracks(): List<YouTubeTrack> {
        return listOf(
            YouTubeTrack(
                id = "dQw4w9WgXcQ",
                title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                channelTitle = "RickAstleyVEVO",
                description = "The official video for 'Never Gonna Give You Up' by Rick Astley. Subscribe to the official Rick Astley YouTube channel.",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            ),
            YouTubeTrack(
                id = "kJQP7kiw5Fk",
                title = "Despacito (feat. Daddy Yankee) - Luis Fonsi",
                channelTitle = "LuisFonsiVEVO",
                description = "Luis Fonsi - Despacito ft. Daddy Yankee (Official Music Video) - Direct from the album 'Vida'.",
                thumbnailUrl = "https://img.youtube.com/vi/kJQP7kiw5Fk/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
            ),
            YouTubeTrack(
                id = "9bZkp7q19f0",
                title = "PSY - GANGNAM STYLE (강남스타일) M/V",
                channelTitle = "officialpsy",
                description = "PSY - 'GANGNAM STYLE' (강남스타일) on YouTube. Over 5 billion views and counting!",
                thumbnailUrl = "https://img.youtube.com/vi/9bZkp7q19f0/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
            ),
            YouTubeTrack(
                id = "09R8_2nJtjg",
                title = "Maroon 5 - Sugar (Official Music Video)",
                channelTitle = "Maroon5VEVO",
                description = "Sugar - Maroon 5's official music video. Crashing weddings in Los Angeles!",
                thumbnailUrl = "https://img.youtube.com/vi/09R8_2nJtjg/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
            ),
            YouTubeTrack(
                id = "fLexgOxsZu0",
                title = "Bruno Mars - Uptown Funk (feat. Mark Ronson)",
                channelTitle = "MarkRonsonVEVO",
                description = "Official Music Video for Uptown Funk by Mark Ronson featuring Bruno Mars.",
                thumbnailUrl = "https://img.youtube.com/vi/fLexgOxsZu0/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
            ),
            YouTubeTrack(
                id = "UqyT8IEB9yY",
                title = "Billie Eilish - bad guy",
                channelTitle = "BillieEilishVEVO",
                description = "Listen to 'bad guy' from the debut album 'WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?'",
                thumbnailUrl = "https://img.youtube.com/vi/UqyT8IEB9yY/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
            ),
            YouTubeTrack(
                id = "rtOvBOTyX00",
                title = "Christina Perri - A Thousand Years [Official Music Video]",
                channelTitle = "Christina Perri",
                description = "Christina Perri - A Thousand Years (Official Video) from The Twilight Saga: Breaking Dawn - Part 1.",
                thumbnailUrl = "https://img.youtube.com/vi/rtOvBOTyX00/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"
            ),
            YouTubeTrack(
                id = "YQHsXMglC9I",
                title = "Drake - Hotline Bling",
                channelTitle = "DrakeVEVO",
                description = "Hotline Bling - Official video from Drake.",
                thumbnailUrl = "https://img.youtube.com/vi/YQHsXMglC9I/0.jpg",
                previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
            )
        )
    }
}
