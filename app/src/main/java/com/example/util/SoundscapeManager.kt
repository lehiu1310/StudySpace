package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sin

object SoundscapeManager {
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var currentMode = "none" // "none", "rain", "binaural", "zen"

    @Synchronized
    fun startSound(mode: String) {
        if (currentMode == mode && isPlaying.get()) return
        stopSound()
        
        if (mode == "none") return
        currentMode = mode
        isPlaying.set(true)
        
        workerThread = thread(start = true, name = "SoundscapeThread", isDaemon = true) {
            runAudioLoop(mode)
        }
    }

    @Synchronized
    fun stopSound() {
        isPlaying.set(false)
        workerThread?.interrupt()
        workerThread = null
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("SoundscapeManager", "Error stopping AudioTrack: ${e.message}")
        }
        audioTrack = null
        currentMode = "none"
    }

    fun getCurrentMode(): String = currentMode

    private fun runAudioLoop(mode: String) {
        val sampleRate = 22050
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096
        
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack = track
            track.play()
            
            val buffer = ShortArray(bufferSize / 2)
            var phaseLeft = 0.0
            var phaseRight = 0.0
            var lastValueLeft = 0.0f
            var lastValueRight = 0.0f
            val random = java.util.Random()
            
            val freqLeft = if (mode == "binaural") 200.0 else 55.0
            val freqRight = if (mode == "binaural") 212.0 else 55.1
            
            while (isPlaying.get() && !Thread.currentThread().isInterrupted) {
                for (i in 0 until buffer.size step 2) {
                    when (mode) {
                        "rain" -> {
                            val noiseL = (random.nextFloat() * 2f - 1f) * 0.15f
                            val noiseR = (random.nextFloat() * 2f - 1f) * 0.15f
                            
                            lastValueLeft = 0.06f * noiseL + 0.94f * lastValueLeft
                            lastValueRight = 0.06f * noiseR + 0.94f * lastValueRight
                            
                            buffer[i] = (lastValueLeft * 32767).toInt().coerceIn(-32768, 32767).toShort()
                            if (i + 1 < buffer.size) {
                                buffer[i + 1] = (lastValueRight * 32767).toInt().coerceIn(-32768, 32767).toShort()
                            }
                        }
                        "binaural" -> {
                            val sampleL = sin(phaseLeft) * 0.35
                            val sampleR = sin(phaseRight) * 0.35
                            
                            phaseLeft += 2.0 * Math.PI * freqLeft / sampleRate
                            phaseRight += 2.0 * Math.PI * freqRight / sampleRate
                            
                            if (phaseLeft > 2.0 * Math.PI) phaseLeft -= 2.0 * Math.PI
                            if (phaseRight > 2.0 * Math.PI) phaseRight -= 2.0 * Math.PI
                            
                            buffer[i] = (sampleL * 32767).toInt().toShort()
                            if (i + 1 < buffer.size) {
                                buffer[i + 1] = (sampleR * 32767).toInt().toShort()
                            }
                        }
                        "zen" -> {
                            val modF = 90.0 + sin(phaseLeft * 0.005) * 5.0
                            val sample = sin(phaseLeft) * 0.3
                            
                            phaseLeft += 2.0 * Math.PI * modF / sampleRate
                            if (phaseLeft > 2.0 * Math.PI) phaseLeft -= 2.0 * Math.PI
                            
                            buffer[i] = (sample * 32767).toInt().toShort()
                            if (i + 1 < buffer.size) {
                                buffer[i + 1] = (sample * 32767).toInt().toShort()
                            }
                        }
                    }
                }
                
                track.write(buffer, 0, buffer.size)
            }
        } catch (e: Exception) {
            Log.e("SoundscapeManager", "Exception in soundscape thread: ${e.message}")
        }
    }
}
