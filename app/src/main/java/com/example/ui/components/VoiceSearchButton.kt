package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen

/**
 * زر الميكروفون بالضغط المطوّل (Hold-to-Speak):
 * - يبدأ الاستماع فور الضغط باستمرار
 * - يعرض تنبيهاً حياً "تحدث الآن... ارفع إصبعك للبحث"
 * - ينفذ البحث أو الانتقال للأقسام فور إفلات الإصبع
 */
@Composable
fun HoldToSpeakMicButton(
    onRecognizedText: (String) -> Unit,
    onStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    testTagName: String = "voice_search_hold_btn"
) {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onRecognizedText)
    val latestOnStatus by rememberUpdatedState(onStatusMessage)

    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            latestOnStatus("تم منح إذن الميكروفون — اضغط مطولاً وتحدث للبحث.")
        } else {
            latestOnStatus("يرجى السماح بصلاحية الميكروفون لاستخدام البحث الصوتي.")
        }
    }

    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(recognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                if (partialTranscript.isNotBlank()) {
                    val fallback = partialTranscript.trim()
                    partialTranscript = ""
                    latestOnResult(fallback)
                } else if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                    latestOnStatus("تعذر التقاط الصوت بوضوح، حاول الضغط مطولاً والتحدث مجدداً.")
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: partialTranscript.trim()
                partialTranscript = ""
                if (spoken.isNotBlank()) {
                    latestOnResult(spoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrBlank()) {
                    partialTranscript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { recognizer?.destroy() }
        }
    }

    val pulse = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) pulseScale else 1f,
        label = "btn_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        AnimatedVisibility(visible = isListening) {
            Surface(
                shape = RoundedCornerShape(50),
                color = SuccessGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, SuccessGreen)
            ) {
                Text(
                    text = if (partialTranscript.isNotBlank()) partialTranscript else "تحدث الآن... ارفع إصبعك",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = if (isListening) DangerRed else MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(
                1.4.dp,
                if (isListening) DangerRed else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            ),
            shadowElevation = if (isListening) 6.dp else 2.dp,
            modifier = Modifier
                .size(48.dp)
                .scale(buttonScale)
                .semantics {
                    contentDescription = "اضغط مطولاً للبحث الصوتي"
                }
                .pointerInput(hasMicPermission, recognizer) {
                    detectTapGestures(
                        onPress = {
                            if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@detectTapGestures
                            }
                            if (recognizer == null) {
                                latestOnStatus("خدمة التعرف الصوتي غير متوفرة على هذا الجهاز.")
                                return@detectTapGestures
                            }
                            partialTranscript = ""
                            isListening = true
                            val recIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-IQ")
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-IQ")
                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                            }
                            runCatching { recognizer.startListening(recIntent) }
                                .onFailure {
                                    isListening = false
                                    latestOnStatus("تعذر تشغيل الميكروفون.")
                                }

                            tryAwaitRelease()

                            runCatching { recognizer.stopListening() }
                            isListening = false
                            if (partialTranscript.isNotBlank()) {
                                val captured = partialTranscript.trim()
                                partialTranscript = ""
                                latestOnResult(captured)
                            }
                        }
                    )
                }
                .testTag(testTagName)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                    contentDescription = "اضغط مطولاً للبحث الصوتي",
                    tint = if (isListening) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
