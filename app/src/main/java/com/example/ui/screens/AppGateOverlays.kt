package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.TeacherProfile
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CustomSplashOverlay(
    profile: TeacherProfile,
    logoFile: File?,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1500L)
        onFinished()
    }

    val primary = MaterialTheme.colorScheme.primary
    val bgTop = MaterialTheme.colorScheme.background
    val bgBot = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBot)))
            .clickable { onFinished() }
            .testTag("custom_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .scale(scale)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val bmp = remember(logoFile?.absolutePath) {
                logoFile?.takeIf { it.exists() }?.let {
                    runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
                }
            }
            if (profile.splashShowLogo && bmp != null) {
                Surface(
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.12f),
                    border = BorderStroke(3.dp, primary),
                    modifier = Modifier.size(116.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "شعار المدرس",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            } else if (profile.splashEmoji.isNotBlank() && profile.splashEmoji != "🧬") {
                Text(text = profile.splashEmoji, fontSize = 54.sp)
            } else {
                // Sleek neutral academic crest (no biology symbol)
                Surface(
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.14f),
                    border = BorderStroke(2.dp, primary.copy(alpha = 0.7f)),
                    modifier = Modifier.size(86.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            if (profile.splashShowName) {
                val displayName = if (profile.teacherName.isNotBlank()) {
                    "أ. ${profile.teacherName}"
                } else {
                    "أهلاً بكم في سجل المدرس الذكي"
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = primary,
                    textAlign = TextAlign.Center
                )
            }

            if (profile.splashGreeting.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = profile.splashGreeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            Text(
                text = "${profile.subjectName} • ${profile.schoolName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppLockGateScreen(
    profile: TeacherProfile,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pulseTransition = rememberInfiniteTransition(label = "fp_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fp_scale"
    )

    val triggerBiometric = {
        launchBiometricPrompt(
            context = context,
            title = "فتح قفل سجل المدرس",
            subtitle = "ضع إصبعك على مستشعر البصمة للدخول",
            onSuccess = onUnlocked,
            onError = { err -> errorMsg = err }
        )
    }

    LaunchedEffect(profile.appLockFingerprintEnabled) {
        if (profile.appLockFingerprintEnabled) {
            triggerBiometric()
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(24.dp)
            .testTag("app_lock_gate"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "التطبيق مقفول لحماية بيانات الطلبة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (profile.teacherName.isNotBlank()) {
                    Text(
                        text = "أ. ${profile.teacherName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primary
                    )
                }

                if (profile.appLockFingerprintEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(primary.copy(alpha = 0.32f), primary.copy(alpha = 0.08f))
                                )
                            )
                            .border(2.5.dp, primary, CircleShape)
                            .clickable { triggerBiometric() }
                            .testTag("circular_fingerprint_pad"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Fingerprint,
                                contentDescription = "بصمة الإصبع",
                                tint = primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "اضغط للبصمة",
                                style = MaterialTheme.typography.labelSmall,
                                color = primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMsg = null
                    },
                    label = { Text("كلمة المرور (أرقام أو حروف)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "إظهار/إخفاء"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_password_input")
                )

                errorMsg?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (passwordInput == profile.appLockPassword) {
                            onUnlocked()
                        } else {
                            errorMsg = "كلمة المرور غير صحيحة"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("app_lock_unlock_btn")
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح التطبيق", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SafeGradeEditUnlockDialog(
    profile: TeacherProfile,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    "الوضع الآمن لتعديل الدرجات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "تعديل الدرجات محمي بالوضع الآمن لمنع التغيير بالخطأ. يرجى التحقق بالبصمة أو كلمة المرور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (profile.appLockFingerprintEnabled) {
                    OutlinedButton(
                        onClick = {
                            launchBiometricPrompt(
                                context = context,
                                title = "تأكيد تعديل الدرجات",
                                subtitle = "استخدم البصمة لفك قفل تعديل الدرجات",
                                onSuccess = onUnlocked,
                                onError = { err = it }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فتح عبر البصمة")
                    }
                }
                OutlinedTextField(
                    value = pass,
                    onValueChange = {
                        pass = it
                        err = null
                    },
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (pass == profile.appLockPassword || pass == profile.vaultPin) {
                                onUnlocked()
                            } else {
                                err = "كلمة المرور غير مطابقة"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تأكيد")
                    }
                }
            }
        }
    }
}
