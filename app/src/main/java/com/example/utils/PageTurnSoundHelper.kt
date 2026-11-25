package com.example.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a gentle, realistic offline paper-flip sound effect (~115ms)
 * without requiring external audio asset files.
 */
object PageTurnSoundHelper {

    @Volatile
    private var lastPlayMs: Long = 0L

    fun playPageTurn(enabled: Boolean = true) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastPlayMs < 140L) return
        lastPlayMs = now

        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val sampleRate = 22050
                val durationMs = 115
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)
                val rng = Random(now)
                var prev = 0.0
                var prev2 = 0.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    // Crisp attack + airy swoosh peak around t = 0.35 + soft paper settle tail
                    val attack = (t / 0.14).coerceIn(0.0, 1.0)
                    val decay = exp(-3.4 * (t - 0.15).coerceAtLeast(0.0))
                    val flutter = 0.78 + 0.22 * sin(t * Math.PI * 3.0)
                    val env = attack * decay * flutter

                    // Band-shaped noise (differentiated & smoothed to sound like crisp paper fiber)
                    val white = rng.nextDouble(-1.0, 1.0)
                    val highPass = white - 0.65 * prev
                    val smoothed = 0.55 * highPass + 0.45 * prev2
                    prev = white
                    prev2 = smoothed

                    val sample = (smoothed * env * 0.28 * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = sample.toShort()
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@runCatching

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes((buffer.size * 2).coerceAtLeast(minBuf))
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 45L)
                track.stop()
                track.release()
            }
        }
    }
}
