package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AlertSoundHelper {

    private val isPlaying = AtomicBoolean(false)

    /**
     * Plays a louder, clear, pleasant chime with a DISTINCT melody for lesson start vs lesson end,
     * followed optionally by a calm male Arabic voice announcement.
     */
    fun playLessonBoundaryAlert(
        context: Context,
        boundaryType: LessonBoundaryType,
        spokenText: String?,
        voiceEnabled: Boolean,
        volumePercent: Int = 95,
        onComplete: (() -> Unit)? = null
    ) {
        triggerLightVibration(context)
        if (!isPlaying.compareAndSet(false, true)) {
            onComplete?.invoke()
            return
        }

        val gain = (volumePercent.coerceIn(20, 100) / 100.0) * 0.85

        Thread {
            var track: AudioTrack? = null
            try {
                val sampleRate = 22050
                val durationSeconds = 4
                val totalSamples = sampleRate * durationSeconds
                val pcm = ShortArray(totalSamples)

                // Distinct musical motifs:
                // BEFORE_START: Rising uplifting major chimes (C5 -> E5 -> G5 -> C6)
                // BEFORE_END: Calm descending closing bell chimes (A5 -> F5 -> D5 -> A4)
                val noteEvents = if (boundaryType == LessonBoundaryType.BEFORE_START) {
                    listOf(
                        0.04 to 523.25,  // C5
                        0.42 to 659.25,  // E5
                        0.80 to 783.99,  // G5
                        1.18 to 1046.50, // C6
                        2.10 to 523.25,
                        2.48 to 659.25,
                        2.86 to 1046.50
                    )
                } else {
                    listOf(
                        0.04 to 880.00,  // A5
                        0.45 to 698.46,  // F5
                        0.86 to 587.33,  // D5
                        1.28 to 440.00,  // A4
                        2.15 to 698.46,
                        2.58 to 587.33,
                        3.00 to 440.00
                    )
                }

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    var sum = 0.0
                    for ((startSec, freq) in noteEvents) {
                        val dt = t - startSec
                        if (dt in 0.0..1.05) {
                            val attack = (dt / 0.02).coerceIn(0.0, 1.0)
                            val decay = exp(-dt * 3.8)
                            val tone = sin(2.0 * PI * freq * dt) * 0.76 +
                                sin(2.0 * PI * (freq * 2.0) * dt) * 0.20 +
                                sin(2.0 * PI * (freq * 0.5) * dt) * 0.12
                            sum += tone * attack * decay
                        }
                    }
                    val tailFade = if (t > 3.55) ((4.0 - t) / 0.45).coerceIn(0.0, 1.0) else 1.0
                    val sample = (sum * gain * tailFade * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = sample.toShort()
                }

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()

                val startMs = System.currentTimeMillis()
                while (isPlaying.get() && System.currentTimeMillis() - startMs < 4050L) {
                    Thread.sleep(120L)
                }
                track.stop()
            } catch (_: Exception) {
            } finally {
                try {
                    track?.release()
                } catch (_: Exception) {
                }
                val stillActive = isPlaying.getAndSet(false)
                if (stillActive && voiceEnabled && !spokenText.isNullOrBlank()) {
                    VoiceAnnouncerHelper.speakArabicCalmMale(
                        context = context.applicationContext,
                        text = spokenText,
                        volumePercent = volumePercent
                    )
                }
                onComplete?.invoke()
            }
        }.start()
    }

    fun playGentleSixSecondAlert(context: Context) {
        playLessonBoundaryAlert(
            context = context,
            boundaryType = LessonBoundaryType.BEFORE_START,
            spokenText = null,
            voiceEnabled = false,
            volumePercent = 95
        )
    }

    fun stopGentleAlert() {
        isPlaying.set(false)
        VoiceAnnouncerHelper.stopSpeaking()
    }

    private fun triggerLightVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(450L)
            }
        } catch (_: Exception) {
        }
    }
}

object VoiceAnnouncerHelper {

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady = false

    fun speakArabicCalmMale(
        context: Context,
        text: String,
        volumePercent: Int = 95
    ) {
        if (text.isBlank()) return
        try {
            val appCtx = context.applicationContext
            val existing = tts
            if (existing != null && isReady) {
                configureAndSpeak(existing, text, volumePercent)
                return
            }
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    engine?.let { configureAndSpeak(it, text, volumePercent) }
                }
            }
            tts = engine
        } catch (_: Exception) {
        }
    }

    private fun configureAndSpeak(engine: TextToSpeech, text: String, volumePercent: Int) {
        try {
            val arIraq = Locale("ar", "IQ")
            val arGeneric = Locale("ar")
            val res = engine.setLanguage(arIraq)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(arGeneric)
            }

            // Pick a male Arabic voice if available on the device TTS engine
            val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            val arabicVoices = voices.filter { v ->
                v.locale.language.equals("ar", ignoreCase = true)
            }
            val maleVoice = arabicVoices.firstOrNull { v ->
                val n = v.name.lowercase()
                n.contains("male") || n.contains("-m-") || n.contains("ard") || n.contains("arz-x-ard") ||
                    (!n.contains("female") && !n.contains("-f-") && !n.contains("ism") && !n.contains("ana"))
            } ?: arabicVoices.firstOrNull()
            if (maleVoice != null) {
                engine.voice = maleVoice
            }

            // Calm, warm, dignified male voice cadence
            engine.setPitch(0.82f)
            engine.setSpeechRate(0.86f)

            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttrs)

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (volumePercent.coerceIn(20, 100) / 100f))
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "lesson_tts_${System.currentTimeMillis()}")
        } catch (_: Exception) {
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }
}
