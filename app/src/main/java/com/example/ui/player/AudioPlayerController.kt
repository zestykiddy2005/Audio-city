package com.example.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.local.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

object AudioPlayerController {
    private const val TAG = "AudioPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    // State flows
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec: StateFlow<Int> = _currentPositionSec.asStateFlow()

    private val _durationSec = MutableStateFlow(0)
    val durationSec: StateFlow<Int> = _durationSec.asStateFlow()

    private val _queue = MutableStateFlow<List<SongEntity>>(emptyList())
    val queue: StateFlow<List<SongEntity>> = _queue.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    // Internal reference for original unshuffled queue
    private var originalQueue: List<SongEntity> = emptyList()
    private var hasTriggeredEndCrossfade = false

    private var progressRunnable: Runnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        val pos = player.currentPosition / 1000
                        _currentPositionSec.value = pos

                        val duration = player.duration / 1000
                        // Seamless crossfade early transition: if within 2 seconds of the end, crossfade to next track!
                        if (duration > 5 && pos >= duration - 2 && !_isRepeat.value) {
                            if (!hasTriggeredEndCrossfade) {
                                hasTriggeredEndCrossfade = true
                                next()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore state issues on secondary fading threads
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        handler.post(progressRunnable)
    }

    private fun fadeInPlayer(player: MediaPlayer, durationMs: Long = 2000) {
        val steps = 10
        val interval = durationMs / steps
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val isPlaying = try { player.isPlaying } catch (ex: Exception) { false }
                    if (isPlaying) {
                        val volume = step.toFloat() / steps
                        player.setVolume(volume, volume)
                        if (step < steps) {
                            step++
                            handler.postDelayed(this, interval)
                        }
                    }
                } catch (e: Exception) {
                    // Player released
                }
            }
        }
        handler.post(runnable)
    }

    private fun fadePlayerOutAndReset(player: MediaPlayer, durationMs: Long = 2000) {
        val steps = 10
        val interval = durationMs / steps
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val isPlaying = try { player.isPlaying } catch (ex: Exception) { false }
                    if (isPlaying) {
                        val volume = 1f - (step.toFloat() / steps)
                        player.setVolume(volume, volume)
                        if (step < steps) {
                            step++
                            handler.postDelayed(this, interval)
                        } else {
                            try { player.stop() } catch (e: Exception) {}
                            try { player.release() } catch (e: Exception) {}
                        }
                    } else {
                        try { player.release() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    try { player.release() } catch (ex: Exception) {}
                }
            }
        }
        handler.post(runnable)
    }

    fun initPlayer(context: Context) {
        // Handled dynamically to support double-player seamless crossfades
    }

    fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        originalQueue = songs
        if (_isShuffle.value) {
            val shuffled = songs.toMutableList()
            if (startIndex in songs.indices) {
                val startSong = songs[startIndex]
                shuffled.remove(startSong)
                shuffled.shuffle()
                shuffled.add(0, startSong)
            } else {
                shuffled.shuffle()
            }
            _queue.value = shuffled
        } else {
            _queue.value = songs
        }

        if (startIndex in _queue.value.indices) {
            playSong(_queue.value[startIndex])
        }
    }

    fun playSong(song: SongEntity) {
        val oldPlayer = mediaPlayer
        _currentSong.value = song
        _isBuffering.value = true
        _isPlaying.value = false
        _currentPositionSec.value = 0
        _durationSec.value = song.durationSec
        hasTriggeredEndCrossfade = false

        val newPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { player ->
                _isBuffering.value = false
                _durationSec.value = player.duration / 1000
                player.start()
                _isPlaying.value = true

                // Start with low volume and fade in
                player.setVolume(0f, 0f)
                fadeInPlayer(player, 2000)

                // Seamlessly fade out the old track if it exists
                if (oldPlayer != null && oldPlayer != player) {
                    try {
                        oldPlayer.setOnCompletionListener(null)
                        oldPlayer.setOnErrorListener(null)
                        val wasPlaying = try { oldPlayer.isPlaying } catch (ex: Exception) { false }
                        if (wasPlaying) {
                            fadePlayerOutAndReset(oldPlayer, 2000)
                        } else {
                            oldPlayer.release()
                        }
                    } catch (e: Exception) {
                        try { oldPlayer.release() } catch (ex: Exception) {}
                    }
                }
            }
            setOnCompletionListener {
                handleTrackCompletion()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                _isBuffering.value = false
                _isPlaying.value = false
                false
            }
        }

        mediaPlayer = newPlayer

        try {
            val dataSource = if (song.isDownloaded && song.localPath != null) {
                song.streamUrl
            } else {
                song.streamUrl
            }
            newPlayer.setDataSource(dataSource)
            newPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song ${song.title}: ${e.message}", e)
            _isBuffering.value = false
            _isPlaying.value = false
        }
    }

    fun play() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                if (_currentSong.value != null) {
                    player.start()
                    _isPlaying.value = true
                } else if (_queue.value.isNotEmpty()) {
                    playSong(_queue.value[0])
                }
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            }
        }
    }

    fun playOrPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    fun seekTo(seconds: Int) {
        mediaPlayer?.let { player ->
            player.seekTo(seconds * 1000)
            _currentPositionSec.value = seconds
        }
    }

    fun next() {
        val current = _currentSong.value ?: return
        val q = _queue.value
        val currentIndex = q.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < q.size - 1) {
            playSong(q[currentIndex + 1])
        } else if (_isRepeat.value && q.isNotEmpty()) {
            playSong(q[0]) // Wrap around
        }
    }

    fun previous() {
        val current = _currentSong.value ?: return
        val q = _queue.value
        val currentIndex = q.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playSong(q[currentIndex - 1])
        } else if (currentIndex == 0 && q.isNotEmpty()) {
            playSong(q[q.size - 1]) // Play last
        }
    }

    fun toggleShuffle() {
        val newValue = !_isShuffle.value
        _isShuffle.value = newValue

        val current = _currentSong.value
        if (newValue) {
            // Shuffle current queue
            val shuffled = originalQueue.toMutableList()
            if (current != null) {
                shuffled.remove(current)
                shuffled.shuffle()
                shuffled.add(0, current)
            } else {
                shuffled.shuffle()
            }
            _queue.value = shuffled
        } else {
            _queue.value = originalQueue
        }
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }

    private fun handleTrackCompletion() {
        if (_isRepeat.value) {
            _currentSong.value?.let { playSong(it) }
        } else {
            next()
        }
    }

    fun addToQueue(song: SongEntity) {
        val currentQueue = _queue.value.toMutableList()
        if (!currentQueue.any { it.id == song.id }) {
            currentQueue.add(song)
            _queue.value = currentQueue
        }
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
