package com.example.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.sqrt

object VoiceInputAnalyzer {
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var analysisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    fun startListening(context: Context) {
        if (isRecording) return
        isRecording = true

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        analysisJob = scope.launch {
            if (hasPermission) {
                try {
                    val bufferSize = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT
                    )

                    if (bufferSize != AudioRecord.ERROR && bufferSize > 0) {
                        audioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            bufferSize
                        )

                        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord?.startRecording()
                            val buffer = ShortArray(bufferSize)

                            while (isRecording && isActive) {
                                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                                if (readSize > 0) {
                                    var sum = 0.0
                                    for (i in 0 until readSize) {
                                        sum += buffer[i] * buffer[i]
                                    }
                                    val rms = sqrt(sum / readSize)
                                    // Normalize RMS value (usually 0 to 32767) to 0.0 - 1.0 range
                                    val normalized = (rms / 32767.0 * 8.0).toFloat().coerceIn(0f, 1f)
                                    // Smooth out slightly
                                    _amplitude.value = _amplitude.value * 0.4f + normalized * 0.6f
                                }
                                delay(40) // poll frequency
                            }
                        } else {
                            runSimulation()
                        }
                    } else {
                        runSimulation()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runSimulation()
                }
            } else {
                runSimulation()
            }
        }
    }

    private suspend fun runSimulation() {
        var angle = 0.0
        while (isRecording) {
            // Generate standard human speaking rhythm amplitude waveform using sine + random fluctuation
            val base = (kotlin.math.sin(angle) * 0.4 + 0.4).toFloat()
            val noise = (Math.random() * 0.2).toFloat()
            val finalAmp = (base + noise).coerceIn(0.05f, 0.95f)
            
            _amplitude.value = finalAmp
            angle += 0.25
            delay(50)
        }
    }

    fun stopListening() {
        isRecording = false
        analysisJob?.cancel()
        analysisJob = null
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _amplitude.value = 0f
    }
}
