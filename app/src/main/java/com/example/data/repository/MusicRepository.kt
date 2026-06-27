package com.example.data.repository

import com.example.data.local.*
import com.example.data.api.BusinessApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import android.util.Log

class MusicRepository(private val musicDao: MusicDao) {

    val allSongs: Flow<List<SongEntity>> = musicDao.getAllSongsFlow()
    val musicTracks: Flow<List<SongEntity>> = musicDao.getMusicTracksFlow()
    val podcasts: Flow<List<SongEntity>> = musicDao.getPodcastsFlow()
    val likedSongs: Flow<List<SongEntity>> = musicDao.getLikedSongsFlow()
    val downloadedSongs: Flow<List<SongEntity>> = musicDao.getDownloadedSongsFlow()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylistsFlow()
    val allNotifications: Flow<List<NotificationEntity>> = musicDao.getAllNotificationsFlow()
    val userActivity: Flow<List<UserActivityEntity>> = musicDao.getUserActivityFlow()
    val currentUser: Flow<UserEntity?> = musicDao.getCurrentUserFlow()

    suspend fun getSongById(songId: String): SongEntity? = musicDao.getSongById(songId)

    suspend fun setLiked(songId: String, isLiked: Boolean) {
        musicDao.updateSongLiked(songId, isLiked)
        val song = musicDao.getSongById(songId)
        logActivity(
            action = if (isLiked) "like" else "unlike",
            songId = songId,
            songTitle = song?.title,
            artistName = song?.artist
        )
        if (isLiked) {
            triggerNotification(
                title = "New Like Recorded",
                message = "You liked '${song?.title}' by ${song?.artist}.",
                type = "like"
            )
        }
    }

    suspend fun setDownloaded(songId: String, isDownloaded: Boolean) {
        val path = if (isDownloaded) "/storage/emulated/0/Android/data/com.aistudio.audiocity/files/downloads/$songId.mp3" else null
        musicDao.updateSongDownloaded(songId, isDownloaded, path)
        val song = musicDao.getSongById(songId)
        logActivity(
            action = if (isDownloaded) "download" else "delete_download",
            songId = songId,
            songTitle = song?.title,
            artistName = song?.artist
        )
        if (isDownloaded) {
            triggerNotification(
                title = "Download Complete",
                message = "Saved '${song?.title}' for offline playback.",
                type = "system"
            )
        }
    }

    suspend fun incrementPlayCount(songId: String) {
        musicDao.incrementPlayCount(songId)
        val song = musicDao.getSongById(songId)
        logActivity(
            action = "play",
            songId = songId,
            songTitle = song?.title,
            artistName = song?.artist
        )
    }

    suspend fun addComment(songId: String, userName: String, photoUrl: String, text: String) {
        val comment = CommentEntity(
            id = UUID.randomUUID().toString(),
            songId = songId,
            userName = userName,
            userPhotoUrl = photoUrl,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        musicDao.insertComment(comment)
        val song = musicDao.getSongById(songId)
        logActivity(
            action = "comment",
            songId = songId,
            songTitle = song?.title,
            artistName = song?.artist
        )
        triggerNotification(
            title = "Comment Added",
            message = "You commented on '${song?.title}': \"$text\"",
            type = "comment"
        )
    }

    fun getCommentsForSong(songId: String): Flow<List<CommentEntity>> {
        return musicDao.getCommentsForSongFlow(songId)
    }

    // --- PLAYLIST ACTIONS ---
    suspend fun createPlaylist(name: String, description: String, coverUrl: String) {
        val playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            coverUrl = coverUrl,
            isCustom = true,
            songCount = 0
        )
        musicDao.insertPlaylist(playlist)
        logActivity("playlist", null, name, "Playlist Created")
        triggerNotification("Playlist Created", "Your new playlist '$name' has been created.", "system")
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
        // Increment count
        val songs = musicDao.getSongsForPlaylistFlow(playlistId).first()
        val playlist = musicDao.getAllPlaylistsFlow().first().find { it.id == playlistId }
        if (playlist != null) {
            musicDao.insertPlaylist(playlist.copy(songCount = songs.size))
        }
        val song = musicDao.getSongById(songId)
        logActivity("playlist", songId, song?.title, "Added to playlist ${playlist?.name}")
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        musicDao.deletePlaylistSongCrossRef(playlistId, songId)
        val songs = musicDao.getSongsForPlaylistFlow(playlistId).first()
        val playlist = musicDao.getAllPlaylistsFlow().first().find { it.id == playlistId }
        if (playlist != null) {
            musicDao.insertPlaylist(playlist.copy(songCount = songs.size))
        }
    }

    fun getSongsForPlaylist(playlistId: String): Flow<List<SongEntity>> = musicDao.getSongsForPlaylistFlow(playlistId)

    suspend fun deletePlaylist(playlistId: String) {
        musicDao.deletePlaylistById(playlistId)
    }

    // --- ARTIST ACTIONS ---
    suspend fun uploadSongByArtist(
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
        status: String = "approved" // "pending_review", "approved", "rejected"
    ) {
        val currentUser = musicDao.getCurrentUserDirect()
        val song = SongEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = currentUser?.name ?: "Unknown Artist",
            album = album,
            durationSec = duration,
            streamUrl = streamUrl,
            coverUrl = coverUrl,
            genre = genre,
            isUploadedByArtist = true,
            artistId = currentUser?.id ?: "anonymous_artist",
            status = status,
            copyrightOwner = copyrightOwner,
            allowDownloads = allowDownloads,
            labelName = labelName,
            isOfficialRelease = isOfficialRelease,
            isrcCode = isrcCode ?: "US-AC1-26-${(10000..99999).random()}"
        )
        musicDao.insertSong(song)
        
        // Update user's uploaded song count
        if (currentUser != null) {
            val updatedUser = currentUser.copy(
                uploadedSongsCount = currentUser.uploadedSongsCount + 1,
                artistModeEnabled = true,
                isArtist = true
            )
            musicDao.insertUser(updatedUser)
        }

        logActivity("upload_submission", song.id, song.title, "Artist Uploaded Status: $status")
        
        if (status == "pending_review") {
            triggerNotification(
                title = "Submission Submitted 📝",
                message = "'${song.title}' has been submitted to the moderation queue for compliance check before going public.",
                type = "system"
            )
        } else {
            triggerNotification(
                title = "New Release Live! 🚀",
                message = "Your song '${song.title}' was approved and is now live worldwide with active CDN caching.",
                type = "release"
            )
        }
    }

    // --- MODERATION ACTIONS ---
    suspend fun moderateSong(songId: String, newStatus: String, notes: String? = null) {
        val song = musicDao.getSongById(songId)
        if (song != null) {
            val updated = song.copy(status = newStatus)
            musicDao.insertSong(updated)
            
            val title = if (newStatus == "approved") "Content Published! ✅" else "Content Flagged ⚠️"
            val body = if (newStatus == "approved") {
                "Congratulations! Your track '${song.title}' has passed our validation review and is now live."
            } else {
                "Your track '${song.title}' was flagged: ${notes ?: "Compliance check failed."}"
            }
            
            triggerNotification(title, body, "system")
            logActivity("moderation_decision", songId, song.title, "Decision: $newStatus")
        }
    }

    // --- COPYRIGHT & TAKEDOWN PORTAL ---
    suspend fun fileCopyrightReport(songId: String, claimantEmail: String, claimDetails: String) {
        val song = musicDao.getSongById(songId)
        if (song != null) {
            // Flag the track immediately by moving it back to pending/flagged state
            val flagged = song.copy(status = "rejected", copyrightOwner = "DISPUTED: $claimDetails")
            musicDao.insertSong(flagged)
            
            triggerNotification(
                title = "DMCA Report Processed ⚖️",
                message = "DMCA claim on '${song.title}' submitted by $claimantEmail. Track restricted pending investigation.",
                type = "system"
            )
            logActivity("dmca_takedown", songId, song.title, "Claimant: $claimantEmail")
        }
    }

    // --- ARTIST PROFILE VERIFICATION SYSTEM ---
    suspend fun submitArtistVerification(
        bio: String,
        genre: String,
        website: String,
        accountType: String // "Musician", "DJ", "Producer", "Label"
    ) {
        val currentUser = musicDao.getCurrentUserDirect()
        if (currentUser != null) {
            val isLabelAcc = accountType == "Label"
            val verifiedUser = currentUser.copy(
                isArtist = !isLabelAcc,
                isLabel = isLabelAcc,
                artistBio = bio,
                artistGenre = genre,
                artistWebsite = website,
                isVerifiedArtist = true, // Instant sandbox verification
                artistModeEnabled = true
            )
            musicDao.insertUser(verifiedUser)
            triggerNotification(
                title = "Verified Badge Approved 🏅",
                message = "Your identity as a $accountType is verified! Official badge and copyright portal unlocked.",
                type = "system"
            )
            logActivity("artist_verification", currentUser.id, currentUser.name, "Type: $accountType")
        }
    }

    // --- USER PROFILE ACTIONS ---
    suspend fun updateUserProfile(name: String, email: String, photoUrl: String, artistMode: Boolean) {
        val existing = musicDao.getCurrentUserDirect()
        val updated = existing?.copy(
            name = name,
            email = email,
            photoUrl = photoUrl,
            artistModeEnabled = artistMode
        ) ?: UserEntity(
            id = "current_user_id",
            name = name,
            email = email,
            photoUrl = photoUrl,
            isPremium = false,
            artistModeEnabled = artistMode
        )
        musicDao.insertUser(updated)
        triggerNotification("Profile Updated", "Your profile details have been successfully saved.", "system")
    }

    suspend fun setPremiumStatus(isPremium: Boolean, planName: String?) {
        val existing = musicDao.getCurrentUserDirect()
        if (existing != null) {
            musicDao.insertUser(existing.copy(isPremium = isPremium, planName = planName))
        } else {
            musicDao.insertUser(UserEntity(
                id = "current_user_id",
                email = "zestykiddy2005@gmail.com",
                name = "Zesty Kiddy",
                photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=300&auto=format&fit=crop",
                isPremium = isPremium,
                planName = planName
            ))
        }
        triggerNotification(
            title = if (isPremium) "Premium Activated! 🎉" else "Subscription Cancelled",
            message = if (isPremium) "Welcome to Audio City Premium! All luxury features, unlimited high-fidelity downloads, and voice assistants are fully enabled." else "Your premium subscription was cancelled.",
            type = "system"
        )
    }

    // --- ACTIVITY LOGS ---
    suspend fun logActivity(action: String, songId: String?, songTitle: String?, artistName: String?) {
        musicDao.insertUserActivity(
            UserActivityEntity(
                id = UUID.randomUUID().toString(),
                action = action,
                songId = songId,
                songTitle = songTitle,
                artistName = artistName,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun triggerNotification(title: String, message: String, type: String) {
        musicDao.insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = type,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
        )
    }

    suspend fun markNotificationAsRead(id: String) {
        musicDao.markNotificationAsRead(id)
    }

    suspend fun markAllNotificationsAsRead() {
        musicDao.markAllNotificationsAsRead()
    }

    // --- SEED DATABASE ---
    suspend fun seedDatabaseIfEmpty() {
        // Only seed if empty
        val currentSongs = musicDao.getAllSongsFlow().first()
        if (currentSongs.isNotEmpty()) return

        // 1. Seed User with Initial Account Roles
        musicDao.insertUser(
            UserEntity(
                id = "current_user_id",
                email = "zestykiddy2005@gmail.com",
                name = "Zesty Kiddy",
                photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=300&auto=format&fit=crop",
                isPremium = false,
                planName = null,
                followersCount = 184,
                followingCount = 42,
                artistModeEnabled = false,
                isArtist = false,
                isLabel = false,
                isModerator = true, // Enable moderator control for the sandbox user to allow them to test approving uploads!
                isVerifiedArtist = false
            )
        )

        // 2. Seed Songs with licensing, labels, global ranks, ISRCs, downloads controls, and status
        val songsList = listOf(
            SongEntity(
                id = "song_1",
                title = "Summer Lofi Chill",
                artist = "Aura Beats",
                album = "Lofi Horizons",
                durationSec = 135,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                coverUrl = "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?q=80&w=300&auto=format&fit=crop",
                genre = "Lofi",
                playCount = 14500,
                status = "approved",
                copyrightOwner = "Aura Beats Publishing / DistroKid",
                allowDownloads = true,
                labelName = "Indie Blue Records",
                isOfficialRelease = true,
                globalRank = 3,
                isrcCode = "US-DIS-26-10394"
            ),
            SongEntity(
                id = "song_2",
                title = "Synthwave Sunset",
                artist = "Vector Core",
                album = "Neon Highways",
                durationSec = 218,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=300&auto=format&fit=crop",
                genre = "Synthwave",
                playCount = 28900,
                status = "approved",
                copyrightOwner = "Sony Music Entertainment",
                allowDownloads = false, // Restricted download permission by major label!
                labelName = "Columbia Records",
                isOfficialRelease = true,
                globalRank = 1,
                isrcCode = "US-SME-26-89402"
            ),
            SongEntity(
                id = "song_3",
                title = "Acoustic Morning",
                artist = "Ember Wood",
                album = "Forest Pines",
                durationSec = 182,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                coverUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?q=80&w=300&auto=format&fit=crop",
                genre = "Acoustic",
                playCount = 12000,
                status = "approved",
                copyrightOwner = "Ember Wood Folk Co.",
                allowDownloads = true,
                labelName = "TuneCore Independent",
                isOfficialRelease = false,
                globalRank = 5,
                isrcCode = "US-TNC-26-40391"
            ),
            SongEntity(
                id = "song_4",
                title = "Cyberpunk Alley",
                artist = "Zero-X",
                album = "Matrix Glitch",
                durationSec = 242,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                coverUrl = "https://images.unsplash.com/photo-1578894381163-e72c17f2d45f?q=80&w=300&auto=format&fit=crop",
                genre = "Electronic",
                playCount = 35600,
                status = "approved",
                copyrightOwner = "Universal Music Group",
                allowDownloads = false, // Content owner turned off downloads!
                labelName = "Interscope Records",
                isOfficialRelease = true,
                globalRank = 2,
                isrcCode = "US-UMG-26-92840"
            ),
            SongEntity(
                id = "song_5",
                title = "Deep Space Ambient",
                artist = "Cosmic Mind",
                album = "Aether Travel",
                durationSec = 305,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                coverUrl = "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?q=80&w=300&auto=format&fit=crop",
                genre = "Ambient",
                playCount = 7600,
                status = "approved",
                copyrightOwner = "Cosmic Mind Ambient",
                allowDownloads = true,
                labelName = "Aether Waves",
                isOfficialRelease = false,
                globalRank = 8,
                isrcCode = "GB-AET-26-30291"
            ),
            SongEntity(
                id = "song_6",
                title = "Focus Study Wave",
                artist = "Mind Gym",
                album = "Alpha Waves",
                durationSec = 154,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                coverUrl = "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?q=80&w=300&auto=format&fit=crop",
                genre = "Study",
                playCount = 11100,
                status = "approved",
                copyrightOwner = "Mind Gym Collective",
                allowDownloads = true,
                labelName = "Focus Labs",
                isOfficialRelease = true,
                globalRank = 6,
                isrcCode = "US-FLB-26-89211"
            ),
            // Seed a track PENDING MODERATION to demonstrate the pre-publication moderation tool!
            SongEntity(
                id = "song_pending_1",
                title = "Hyper Disco Beats",
                artist = "DJ Neon Spark",
                album = "Vibrant Nights",
                durationSec = 190,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=300&auto=format&fit=crop",
                genre = "Electronic",
                playCount = 0,
                status = "pending_review", // Moderation pending!
                copyrightOwner = "DJ Neon Spark Collective",
                allowDownloads = true,
                labelName = "Independent Distributor Feed",
                isOfficialRelease = false,
                isrcCode = "US-MOD-26-00001"
            ),
            SongEntity(
                id = "song_pending_2",
                title = "Chill Hop Vibes",
                artist = "Lofi Pioneer",
                album = "Vintage Coffee",
                durationSec = 145,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?q=80&w=300&auto=format&fit=crop",
                genre = "Lofi",
                playCount = 0,
                status = "pending_review", // Moderation pending!
                copyrightOwner = "Lofi Pioneer Independent",
                allowDownloads = true,
                labelName = "FUGA Distribution Feed",
                isOfficialRelease = true,
                isrcCode = "NL-FUG-26-12003"
            )
        )
        musicDao.insertSongs(songsList)

        // 3. Seed Playlists
        val playlists = listOf(
            PlaylistEntity("pl_lofi", "Lofi Chill Out", "A beautiful ambient list for work and relaxation", "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?q=80&w=300", false, 2),
            PlaylistEntity("pl_electronic", "Electro Rush", "High bpm synthesis and cyberpunk cybernetic journeys", "https://images.unsplash.com/photo-1578894381163-e72c17f2d45f?q=80&w=300", false, 2)
        )
        for (pl in playlists) {
            musicDao.insertPlaylist(pl)
        }

        // Add cross refs
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("pl_lofi", "song_1"))
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("pl_lofi", "song_6"))
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("pl_electronic", "song_2"))
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef("pl_electronic", "song_4"))

        // 4. Seed Comments
        val comments = listOf(
            CommentEntity("c1", "song_1", "Sarah K.", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=300", "This lofi beat is pure absolute bliss, gets me in the zone instantly!", System.currentTimeMillis() - 7200000),
            CommentEntity("c2", "song_1", "Marcus V.", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=300", "Production quality on this is 10/10.", System.currentTimeMillis() - 3600000),
            CommentEntity("c3", "song_2", "RavePilot", "https://images.unsplash.com/photo-1488161628813-04466f872be2?q=80&w=300", "Brings back that nostalgic 80s arcade feel. Incredible!", System.currentTimeMillis() - 14400000)
        )
        for (com in comments) {
            musicDao.insertComment(com)
        }

        // 5. Seed Notifications
        val notifications = listOf(
            NotificationEntity("n1", "Welcome to Audio City Production! 🎧", "Audio City is now a live global commercial platform. Access verified profiles, submit licensing papers, and track play statistics.", "system", System.currentTimeMillis() - 86400000, true),
            NotificationEntity("n2", "Aura Beats uploaded a track!", "Listen to 'Summer Lofi Chill' now in discover.", "release", System.currentTimeMillis() - 43200000, false),
            NotificationEntity("n3", "Dr. Cole followed you", "Your profile is growing. Keep engaging with the audio city community!", "follow", System.currentTimeMillis() - 1800000, false)
        )
        for (notif in notifications) {
            musicDao.insertNotification(notif)
        }

        // 6. Seed Activities
        val activities = listOf(
            UserActivityEntity("a1", "play", "song_1", "Summer Lofi Chill", "Aura Beats", System.currentTimeMillis() - 7200000),
            UserActivityEntity("a2", "like", "song_1", "Summer Lofi Chill", "Aura Beats", System.currentTimeMillis() - 7100000),
            UserActivityEntity("a3", "play", "song_3", "Acoustic Morning", "Ember Wood", System.currentTimeMillis() - 3600000)
        )
        for (act in activities) {
            musicDao.insertUserActivity(act)
        }
    }

    // --- CLOUD DATABASE & BUSINESS API SYNC ---
    
    /**
     * Fetches all official songs from your custom production/business database (REST API)
     * and synchronizes them directly into the local Room cache database.
     */
    suspend fun syncWithBusinessCloudApi(authToken: String? = null): Result<List<SongEntity>> {
        return try {
            Log.d("MusicRepository", "Syncing local Room database with business cloud API...")
            val response = BusinessApiClient.apiService.getSongsCatalog(authToken)
            if (response.status == "success" && response.data != null) {
                val syncedSongs = response.data.map { remoteSong ->
                    val localSong = remoteSong.toLocalEntity()
                    musicDao.insertSong(localSong)
                    localSong
                }
                Log.d("MusicRepository", "Successfully synchronized ${syncedSongs.size} songs to Room Database.")
                Result.success(syncedSongs)
            } else {
                val errMsg = response.message ?: "Unknown business API response error"
                Log.e("MusicRepository", "Cloud sync response failed: $errMsg")
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error syncing with business cloud API database", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads a song created in the app (e.g., from the Artist panel) 
     * directly to your production cloud database.
     */
    suspend fun uploadSongToBusinessCloudApi(
        authToken: String,
        song: SongEntity
    ): Result<SongEntity> {
        return try {
            val remoteSong = com.example.data.api.RemoteSongResponse(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationSec = song.durationSec,
                streamUrl = song.streamUrl,
                coverUrl = song.coverUrl,
                genre = song.genre,
                isOfficialRelease = song.isOfficialRelease,
                allowDownloads = song.allowDownloads
            )
            val response = BusinessApiClient.apiService.uploadNewSong(authToken, remoteSong)
            if (response.status == "success" && response.data != null) {
                val savedLocal = response.data.toLocalEntity()
                musicDao.insertSong(savedLocal)
                Result.success(savedLocal)
            } else {
                Result.failure(Exception(response.message ?: "Failed to upload song to business cloud."))
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error uploading to business cloud API", e)
            Result.failure(e)
        }
    }
}
