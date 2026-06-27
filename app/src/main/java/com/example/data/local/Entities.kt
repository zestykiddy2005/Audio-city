package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val streamUrl: String,
    val coverUrl: String,
    val genre: String,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val isLiked: Boolean = false,
    val isPodcast: Boolean = false,
    val playCount: Int = 0,
    val isUploadedByArtist: Boolean = false,
    val artistId: String? = null,
    val status: String = "approved", // "pending_review", "approved", "rejected"
    val copyrightOwner: String = "Authorized Content Feed",
    val allowDownloads: Boolean = true,
    val labelName: String? = null,
    val isOfficialRelease: Boolean = false,
    val viewCount: Int = 0,
    val globalRank: Int = 0,
    val isrcCode: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val isCustom: Boolean = true,
    val songCount: Int = 0
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val photoUrl: String,
    val isPremium: Boolean = false,
    val planName: String? = null,
    val followersCount: Int = 124,
    val followingCount: Int = 56,
    val artistModeEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isArtist: Boolean = false,
    val isLabel: Boolean = false,
    val isModerator: Boolean = false,
    val artistBio: String? = null,
    val artistGenre: String? = null,
    val artistWebsite: String? = null,
    val isVerifiedArtist: Boolean = false,
    val uploadedSongsCount: Int = 0,
    val officialLabelId: String? = null
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String, // "follow", "like", "comment", "release", "system"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "user_activity")
data class UserActivityEntity(
    @PrimaryKey val id: String,
    val action: String, // "play", "like", "download", "comment", "playlist"
    val songId: String?,
    val songTitle: String?,
    val artistName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val userName: String,
    val userPhotoUrl: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
