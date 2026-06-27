package com.example.ui.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.CommentEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongEntity
import com.example.data.local.UserActivityEntity
import com.example.data.local.UserEntity
import com.example.data.api.Content
import com.example.data.api.GeminiManager
import com.example.data.api.Part
import com.example.data.repository.MusicRepository
import com.example.ui.player.AudioPlayerController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "audio_city_db"
    ).fallbackToDestructiveMigration().build()

    private val repository = MusicRepository(db.musicDao())

    // --- Player States ---
    val currentSong = AudioPlayerController.currentSong
    val isPlaying = AudioPlayerController.isPlaying
    val isBuffering = AudioPlayerController.isBuffering
    val currentPositionSec = AudioPlayerController.currentPositionSec
    val durationSec = AudioPlayerController.durationSec
    val playerQueue = AudioPlayerController.queue
    val isShuffle = AudioPlayerController.isShuffle
    val isRepeat = AudioPlayerController.isRepeat

    // --- DB Flow States ---
    val allSongs = repository.allSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val musicTracks = repository.musicTracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val podcasts = repository.podcasts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedSongs = repository.likedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloadedSongs = repository.downloadedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPlaylists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allNotifications = repository.allNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val userActivity = repository.userActivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentUser = repository.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Search State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResultSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val searchResultSongs: StateFlow<List<SongEntity>> = _searchResultSongs.asStateFlow()

    // --- UI State: Active Song Comments ---
    private val _currentSongComments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val currentSongComments: StateFlow<List<CommentEntity>> = _currentSongComments.asStateFlow()

    // --- AI Feature States ---
    private val _lyricsState = MutableStateFlow<String>("")
    val lyricsState: StateFlow<String> = _lyricsState.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _searchGroundingResult = MutableStateFlow<String>("")
    val searchGroundingResult: StateFlow<String> = _searchGroundingResult.asStateFlow()

    private val _searchGroundingSources = MutableStateFlow<List<String>>(emptyList())
    val searchGroundingSources: StateFlow<List<String>> = _searchGroundingSources.asStateFlow()

    private val _searchGroundingLoading = MutableStateFlow(false)
    val searchGroundingLoading: StateFlow<Boolean> = _searchGroundingLoading.asStateFlow()

    private val _aiRecommendation = MutableStateFlow("")
    val aiRecommendation: StateFlow<String> = _aiRecommendation.asStateFlow()

    // --- Voice Companion States ---
    private val _voiceCompanionHistory = MutableStateFlow<List<Content>>(emptyList())
    val voiceCompanionHistory: StateFlow<List<Content>> = _voiceCompanionHistory.asStateFlow()

    private val _voiceCompanionStatus = MutableStateFlow("Tap mic to converse with City Companion AI.")
    val voiceCompanionStatus: StateFlow<String> = _voiceCompanionStatus.asStateFlow()

    private val _voiceCompanionLoading = MutableStateFlow(false)
    val voiceCompanionLoading: StateFlow<Boolean> = _voiceCompanionLoading.asStateFlow()

    // --- High Thinking State ---
    private val _highThinkingResult = MutableStateFlow("")
    val highThinkingResult: StateFlow<String> = _highThinkingResult.asStateFlow()

    private val _highThinkingLoading = MutableStateFlow(false)
    val highThinkingLoading: StateFlow<Boolean> = _highThinkingLoading.asStateFlow()

    // --- Upload Status ---
    private val _uploadProgress = MutableStateFlow<Int?>(null) // null = idle, 100 = completed
    val uploadProgress: StateFlow<Int?> = _uploadProgress.asStateFlow()

    // --- Download Progress State ---
    private val _downloadingSongIds = MutableStateFlow<Map<String, Int>>(emptyMap()) // SongId -> Percent
    val downloadingSongIds: StateFlow<Map<String, Int>> = _downloadingSongIds.asStateFlow()

    // --- YouTube Integration States ---
    private val _youtubeSearchQuery = MutableStateFlow("")
    val youtubeSearchQuery: StateFlow<String> = _youtubeSearchQuery.asStateFlow()

    private val _youtubeSearchResultTracks = MutableStateFlow<List<com.example.data.api.YouTubeTrack>>(emptyList())
    val youtubeSearchResultTracks: StateFlow<List<com.example.data.api.YouTubeTrack>> = _youtubeSearchResultTracks.asStateFlow()

    private val _youtubeArtistMetrics = MutableStateFlow<com.example.data.api.YouTubeTrack?>(null)
    val youtubeArtistMetrics: StateFlow<com.example.data.api.YouTubeTrack?> = _youtubeArtistMetrics.asStateFlow()

    private val _youtubeArtistTracks = MutableStateFlow<List<com.example.data.api.YouTubeTrack>>(emptyList())
    val youtubeArtistTracks: StateFlow<List<com.example.data.api.YouTubeTrack>> = _youtubeArtistTracks.asStateFlow()

    private val _isYouTubeSyncing = MutableStateFlow(false)
    val isYouTubeSyncing: StateFlow<Boolean> = _isYouTubeSyncing.asStateFlow()

    init {
        // Init media player controller
        AudioPlayerController.initPlayer(application)

        viewModelScope.launch {
            repository.seedDatabaseIfEmpty()
            loadLyricsForCurrentSong()
            generateAiMusicRecommendation()
        }

        // Listen for current song changes to automatically load lyrics and comment streams
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null) {
                    loadLyricsForCurrentSong()
                    // Collect comments reactively
                    repository.getCommentsForSong(song.id).collect {
                        _currentSongComments.value = it
                    }
                } else {
                    _currentSongComments.value = emptyList()
                    _lyricsState.value = ""
                }
            }
        }

        // Filter search list automatically
        viewModelScope.launch {
            combine(searchQuery, allSongs) { query, songs ->
                val approvedSongs = songs.filter { it.status == "approved" }
                if (query.isBlank()) {
                    approvedSongs
                } else {
                    approvedSongs.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true) ||
                        it.genre.contains(query, ignoreCase = true) ||
                        (it.labelName?.contains(query, ignoreCase = true) ?: false) ||
                        (it.isrcCode?.contains(query, ignoreCase = true) ?: false)
                    }
                }
            }.collect {
                _searchResultSongs.value = it
            }
        }
    }

    // --- SEARCH METHODS ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun conductSearchGrounding(query: String) {
        viewModelScope.launch {
            _searchGroundingLoading.value = true
            _searchGroundingResult.value = ""
            _searchGroundingSources.value = emptyList()

            val response = GeminiManager.searchGroundingQuery(query)
            _searchGroundingResult.value = response.first
            _searchGroundingSources.value = response.second
            _searchGroundingLoading.value = false

            repository.logActivity("search_grounding", null, query, "Grounding Search")
        }
    }

    // --- VOICE TRANSCRIPTION SEARCH (Speech-To-Text) ---
    fun processVoiceCommand(text: String) {
        val query = text.lowercase().trim()
        viewModelScope.launch {
            when {
                query.contains("pause") || query.contains("stop") -> {
                    _voiceCompanionStatus.value = "Voice Command: Paused playback."
                    AudioPlayerController.pause()
                    repository.logActivity("voice_command", null, text, "Pause via Voice")
                }
                query.contains("resume") || query.contains("play music") || query.contains("start music") -> {
                    _voiceCompanionStatus.value = "Voice Command: Resumed playback."
                    AudioPlayerController.play()
                    repository.logActivity("voice_command", null, text, "Play via Voice")
                }
                query.contains("next") || query.contains("skip") -> {
                    _voiceCompanionStatus.value = "Voice Command: Skipped to next track."
                    next()
                    repository.logActivity("voice_command", null, text, "Next via Voice")
                }
                query.contains("previous") || query.contains("prev") || query.contains("back") -> {
                    _voiceCompanionStatus.value = "Voice Command: Returned to previous track."
                    previous()
                    repository.logActivity("voice_command", null, text, "Prev via Voice")
                }
                query.contains("like") || query.contains("favorite") || query.contains("love") -> {
                    currentSong.value?.let { song ->
                        toggleLike(song.id)
                        _voiceCompanionStatus.value = "Voice Command: Liked '${song.title}'."
                        repository.logActivity("voice_command", song.id, text, "Like via Voice")
                    } ?: run {
                        _voiceCompanionStatus.value = "Voice Command: No active song to like."
                    }
                }
                query.contains("shuffle") -> {
                    toggleShuffle()
                    _voiceCompanionStatus.value = "Voice Command: Toggled shuffle."
                    repository.logActivity("voice_command", null, text, "Shuffle via Voice")
                }
                query.contains("repeat") -> {
                    toggleRepeat()
                    _voiceCompanionStatus.value = "Voice Command: Toggled repeat."
                    repository.logActivity("voice_command", null, text, "Repeat via Voice")
                }
                query.contains("play") -> {
                    val songName = query.substringAfter("play").trim()
                    if (songName.isNotEmpty()) {
                        val match = allSongs.value.find { song ->
                            song.title.contains(songName, ignoreCase = true) || 
                            song.artist.contains(songName, ignoreCase = true)
                        }
                        if (match != null) {
                            _voiceCompanionStatus.value = "Voice Command: Playing '${match.title}'."
                            playSong(match, allSongs.value)
                            repository.logActivity("voice_command", match.id, text, "Play song via Voice")
                        } else {
                            _voiceCompanionStatus.value = "Voice Command: Song '$songName' not found in library. Searching Discover..."
                            _searchQuery.value = songName
                        }
                    } else {
                        AudioPlayerController.play()
                        _voiceCompanionStatus.value = "Voice Command: Started music."
                    }
                }
                else -> {
                    val match = allSongs.value.find { song ->
                        query.contains(song.title.lowercase()) || query.contains(song.artist.lowercase())
                    }
                    if (match != null) {
                        _voiceCompanionStatus.value = "Voice Command: Found and playing '${match.title}'."
                        playSong(match, allSongs.value)
                        repository.logActivity("voice_command", match.id, text, "Play match via Voice")
                    } else {
                        _voiceCompanionStatus.value = "Voice Command not recognized: '$text'. Try 'play [song]', 'pause', 'next'."
                    }
                }
            }
        }
    }

    fun transcribeAndSearch(base64Wav: String) {
        viewModelScope.launch {
            _voiceCompanionStatus.value = "Transcribing spoken query..."
            val text = GeminiManager.transcribeVoiceQuery(base64Wav)
            _searchQuery.value = text
            _voiceCompanionStatus.value = "Voice query transcribed: '$text'"
            repository.logActivity("voice_transcription", null, text, "Voice Transcribe")
            processVoiceCommand(text)
        }
    }

    // --- MUSIC CONTROLS ---
    fun playSong(song: SongEntity, contextList: List<SongEntity>) {
        viewModelScope.launch {
            val idx = contextList.indexOfFirst { it.id == song.id }
            AudioPlayerController.setQueue(contextList, if (idx != -1) idx else 0)
            repository.incrementPlayCount(song.id)
        }
    }

    fun playOrPause() {
        AudioPlayerController.playOrPause()
    }

    fun next() {
        viewModelScope.launch {
            AudioPlayerController.next()
            currentSong.value?.let {
                repository.incrementPlayCount(it.id)
            }
        }
    }

    fun previous() {
        viewModelScope.launch {
            AudioPlayerController.previous()
            currentSong.value?.let {
                repository.incrementPlayCount(it.id)
            }
        }
    }

    fun seekTo(sec: Int) {
        AudioPlayerController.seekTo(sec)
    }

    fun toggleShuffle() {
        AudioPlayerController.toggleShuffle()
    }

    fun toggleRepeat() {
        AudioPlayerController.toggleRepeat()
    }

    fun toggleLike(songId: String) {
        viewModelScope.launch {
            val song = allSongs.value.find { it.id == songId } ?: return@launch
            repository.setLiked(songId, !song.isLiked)
        }
    }

    // --- COMMENTS ---
    fun postComment(text: String) {
        val song = currentSong.value ?: return
        val user = currentUser.value
        viewModelScope.launch {
            repository.addComment(
                songId = song.id,
                userName = user?.name ?: "Premium Guest",
                photoUrl = user?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=300",
                text = text
            )
        }
    }

    // --- PLAYLISTS ---
    fun createPlaylist(name: String, description: String, coverUrl: String = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=300&auto=format&fit=crop") {
        viewModelScope.launch {
            repository.createPlaylist(name, description, coverUrl)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getSongsInPlaylist(playlistId: String): Flow<List<SongEntity>> = repository.getSongsForPlaylist(playlistId)

    // --- LYRICS ---
    private fun loadLyricsForCurrentSong() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            _lyricsLoading.value = true
            _lyricsState.value = ""
            val lyrics = GeminiManager.generateSongLyrics(song.title, song.artist)
            _lyricsState.value = lyrics
            _lyricsLoading.value = false
        }
    }

    // --- AI MUSIC RECOMMENDATION ---
    private fun generateAiMusicRecommendation() {
        viewModelScope.launch {
            val activity = userActivity.value
            val historySummary = if (activity.isEmpty()) {
                "User listening profile: Loves Chill beats, lofi soundtracks, and morning acoustic melodies."
            } else {
                activity.joinToString("\n") { "${it.action} on ${it.songTitle} by ${it.artistName}" }
            }
            _aiRecommendation.value = GeminiManager.recommendSongsBasedOnHistory(historySummary)
        }
    }

    // --- VOICE COMPANION (Live Dialogue Companion) ---
    fun sendVoiceCompanionMessage(text: String) {
        viewModelScope.launch {
            _voiceCompanionLoading.value = true
            _voiceCompanionStatus.value = "Thinking..."

            val response = GeminiManager.talkToVoiceCompanion(_voiceCompanionHistory.value, text)
            
            // Append dialogue
            val historyUpdate = _voiceCompanionHistory.value.toMutableList().apply {
                add(Content(parts = listOf(Part(text = text)), role = "user"))
                add(Content(parts = listOf(Part(text = response.first)), role = "model"))
            }
            _voiceCompanionHistory.value = historyUpdate
            _voiceCompanionStatus.value = "Response spoken! (VoiceName: Kore)"
            _voiceCompanionLoading.value = false

            // Process speech command using the global voice command processor
            processVoiceCommand(text)

            repository.logActivity("voice_assistant", null, text, "Companion Voice")
        }
    }

    fun clearVoiceCompanion() {
        _voiceCompanionHistory.value = emptyList()
        _voiceCompanionStatus.value = "Dialogue reset. Tap mic to converse."
    }

    // --- HIGH THINKING PERSONALIZED 5-DAY CURATION ---
    fun generateHighThinkingCuration() {
        viewModelScope.launch {
            _highThinkingLoading.value = true
            _highThinkingResult.value = ""

            val profile = currentUser.value?.let { "${it.name} (${it.email})" } ?: "Premium Listener"
            val activity = userActivity.value
            val historySummary = if (activity.isEmpty()) {
                "Recent actions: Listened to Aura Beats, liked lofi track, liked study melodies."
            } else {
                activity.joinToString("\n") { "${it.action} - ${it.songTitle} by ${it.artistName}" }
            }

            val result = GeminiManager.highThinkingCurationPlan(profile, historySummary)
            _highThinkingResult.value = result
            _highThinkingLoading.value = false

            repository.logActivity("high_thinking_curation", null, "5-Day Plan", "Thinking Model")
        }
    }

    // --- DOWNLOAD SIMULATOR ---
    fun downloadSong(songId: String) {
        viewModelScope.launch {
            if (_downloadingSongIds.value.containsKey(songId)) return@launch

            // Simulate progress beautifully
            val currentMap = _downloadingSongIds.value.toMutableMap()
            currentMap[songId] = 0
            _downloadingSongIds.value = currentMap

            // Post progress values
            for (progress in 10..100 step 15) {
                kotlinx.coroutines.delay(200)
                val map = _downloadingSongIds.value.toMutableMap()
                map[songId] = progress
                _downloadingSongIds.value = map
            }

            // Mark downloaded in DB
            repository.setDownloaded(songId, true)

            // Clear downloading state
            val map = _downloadingSongIds.value.toMutableMap()
            map.remove(songId)
            _downloadingSongIds.value = map
        }
    }

    fun removeDownloadedSong(songId: String) {
        viewModelScope.launch {
            repository.setDownloaded(songId, false)
        }
    }

    // --- ARTIST UPLOAD FLOW ---
    fun artistUploadSong(
        title: String,
        album: String,
        duration: Int,
        genre: String,
        streamUrl: String,
        coverUrl: String,
        copyrightOwner: String = "Authorized Artist Release",
        allowDownloads: Boolean = true,
        labelName: String? = null,
        isOfficialRelease: Boolean = false,
        isrcCode: String? = null,
        status: String = "approved"
    ) {
        viewModelScope.launch {
            _uploadProgress.value = 0
            // Simulated upload steps (shows scalable cloud upload progress)
            for (p in 10..100 step 20) {
                kotlinx.coroutines.delay(100)
                _uploadProgress.value = p
            }
            // Save to database
            repository.uploadSongByArtist(
                title = title,
                album = album,
                duration = duration,
                genre = genre,
                streamUrl = streamUrl,
                coverUrl = coverUrl,
                copyrightOwner = copyrightOwner,
                allowDownloads = allowDownloads,
                labelName = labelName,
                isOfficialRelease = isOfficialRelease,
                isrcCode = isrcCode,
                status = status
            )
            _uploadProgress.value = null
        }
    }

    fun moderateSong(songId: String, newStatus: String, notes: String? = null) {
        viewModelScope.launch {
            repository.moderateSong(songId, newStatus, notes)
        }
    }

    fun submitArtistVerification(bio: String, genre: String, website: String, accountType: String) {
        viewModelScope.launch {
            repository.submitArtistVerification(bio, genre, website, accountType)
        }
    }

    fun fileCopyrightReport(songId: String, claimantEmail: String, claimDetails: String) {
        viewModelScope.launch {
            repository.fileCopyrightReport(songId, claimantEmail, claimDetails)
        }
    }

    // --- USER PROFILE & AUTH SIMULATOR ---
    fun saveUserProfile(name: String, email: String, photoUrl: String, artistMode: Boolean) {
        viewModelScope.launch {
            repository.updateUserProfile(name, email, photoUrl, artistMode)
        }
    }

    fun handlePremiumSubscriptionCheckout(cardNo: String, holder: String, method: String, plan: String) {
        viewModelScope.launch {
            // Log premium activity & update DB
            repository.setPremiumStatus(true, plan)
            repository.logActivity("checkout_premium", null, plan, "Payment method: $method")
        }
    }

    fun cancelPremiumSubscription() {
        viewModelScope.launch {
            repository.setPremiumStatus(false, null)
            repository.logActivity("cancel_premium", null, "Cancel", "Unsubscribed")
        }
    }

    // --- ADMIN SYSTEM MODERATION & BROADCAST METHODS ---
    fun adminDeleteTrack(songId: String) {
        viewModelScope.launch {
            val song = allSongs.value.find { it.id == songId } ?: return@launch
            db.musicDao().deleteSong(song)
            repository.triggerNotification(
                title = "Content Moderated ⚠️",
                message = "The track '${song.title}' by ${song.artist} has been flagged and removed by system administration.",
                type = "system"
            )
            repository.logActivity("admin_delete", songId, song.title, "Admin Deleted")
        }
    }

    fun adminBroadcastAnnouncement(title: String, body: String) {
        viewModelScope.launch {
            repository.triggerNotification(
                title = "ANNouncement 📢 - $title",
                message = body,
                type = "system"
            )
            repository.logActivity("admin_broadcast", null, title, "Admin Broadcast")
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notifId)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    // --- YOUTUBE INTEGRATION METHODS ---
    fun updateYouTubeSearchQuery(query: String) {
        _youtubeSearchQuery.value = query
        if (query.isBlank()) {
            _youtubeSearchResultTracks.value = emptyList()
            return
        }
        viewModelScope.launch {
            val results = com.example.data.api.YouTubeManager.searchTracks(query)
            _youtubeSearchResultTracks.value = results
        }
    }

    fun syncArtistYouTubeMetrics(artistName: String) {
        viewModelScope.launch {
            _isYouTubeSyncing.value = true
            try {
                val results = com.example.data.api.YouTubeManager.searchTracks(artistName)
                if (results.isNotEmpty()) {
                    _youtubeArtistMetrics.value = results.first()
                    _youtubeArtistTracks.value = results
                    
                    // Trigger dynamic notifications of success
                    repository.triggerNotification(
                        title = "YouTube Creator Sync Active 🟢",
                        message = "Successfully synced streaming feed and audience data for artist '$artistName' from YouTube directory catalog.",
                        type = "system"
                    )
                    repository.logActivity("youtube_sync", results.first().id, artistName, "Synced YouTube video metrics")
                } else {
                    _youtubeArtistMetrics.value = null
                    _youtubeArtistTracks.value = emptyList()
                }
            } catch (e: Exception) {
                _youtubeArtistMetrics.value = null
                _youtubeArtistTracks.value = emptyList()
            } finally {
                _isYouTubeSyncing.value = false
            }
        }
    }

    fun playYouTubeTrack(track: com.example.data.api.YouTubeTrack, allTracks: List<com.example.data.api.YouTubeTrack>) {
        viewModelScope.launch {
            // Transform YouTube tracks to SongEntity items
            val songEntities = allTracks.map { t ->
                SongEntity(
                    id = "youtube_${t.id}",
                    title = t.title,
                    artist = t.channelTitle,
                    album = "YouTube Release",
                    durationSec = 240, // default placeholder duration for streaming video audio
                    streamUrl = t.previewUrl,
                    coverUrl = t.thumbnailUrl,
                    genre = "YouTube Music",
                    isUploadedByArtist = false,
                    copyrightOwner = "YouTube LLC Licensed Audio Feed",
                    allowDownloads = false
                )
            }
            val currentEntity = SongEntity(
                id = "youtube_${track.id}",
                title = track.title,
                artist = track.channelTitle,
                album = "YouTube Release",
                durationSec = 240,
                streamUrl = track.previewUrl,
                coverUrl = track.thumbnailUrl,
                genre = "YouTube Music",
                isUploadedByArtist = false,
                copyrightOwner = "YouTube LLC Licensed Audio Feed",
                allowDownloads = false
            )
            
            val idx = songEntities.indexOfFirst { it.id == currentEntity.id }
            AudioPlayerController.setQueue(songEntities, if (idx != -1) idx else 0)
            repository.logActivity("youtube_playback", currentEntity.id, currentEntity.title, "Stream audio feed from YouTube servers")
        }
    }

    override fun onCleared() {
        super.onCleared()
        AudioPlayerController.release()
        db.close()
    }
}
