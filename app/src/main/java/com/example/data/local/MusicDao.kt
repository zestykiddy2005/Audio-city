package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // --- SONGS ---
    @Query("SELECT * FROM songs ORDER BY playCount DESC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isPodcast = 0 ORDER BY playCount DESC")
    fun getMusicTracksFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isPodcast = 1")
    fun getPodcastsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE isLiked = 1")
    fun getLikedSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    fun getDownloadedSongsFlow(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    suspend fun updateSongLiked(songId: String, isLiked: Boolean)

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localPath = :localPath WHERE id = :songId")
    suspend fun updateSongDownloaded(songId: String, isDownloaded: Boolean, localPath: String?)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    // --- PLAYLISTS ---
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    // --- PLAYLIST SONGS ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSongCrossRef(playlistId: String, songId: String)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_song_cross_ref r ON s.id = r.songId 
        WHERE r.playlistId = :playlistId
    """)
    fun getSongsForPlaylistFlow(playlistId: String): Flow<List<SongEntity>>

    // --- COMMENTS ---
    @Query("SELECT * FROM comments WHERE songId = :songId ORDER BY timestamp DESC")
    fun getCommentsForSongFlow(songId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    // --- USER PROFILE ---
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUserDirect(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    // --- USER ACTIVITY ---
    @Query("SELECT * FROM user_activity ORDER BY timestamp DESC LIMIT 50")
    fun getUserActivityFlow(): Flow<List<UserActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserActivity(activity: UserActivityEntity)
}
