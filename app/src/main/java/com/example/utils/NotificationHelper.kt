package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.JsonStorageRepository

class LessonNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "تنبيه الدرس"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "اقترب موعد الحصة الدراسية"
        val id = intent.getIntExtra(EXTRA_ID, 1001)
        val playSound = intent.getBooleanExtra(EXTRA_PLAY_SOUND, true)
        val boundaryType = if (intent.getStringExtra(EXTRA_BOUNDARY_TYPE) == "end") {
            LessonBoundaryType.BEFORE_END
        } else {
            LessonBoundaryType.BEFORE_START
        }
        val spokenText = intent.getStringExtra(EXTRA_SPOKEN_TEXT)
        val voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, true)
        val volumePercent = intent.getIntExtra(EXTRA_VOLUME_PERCENT, 95)

        NotificationHelper.showNotification(context, id, title, body)
        if (playSound) {
            val pending = goAsync()
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = runCatching {
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rafiq:LessonAlarmWake").apply {
                    acquire(9500L)
                }
            }.getOrNull()

            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finishSafely() {
                if (finished.compareAndSet(false, true)) {
                    runCatching {
                        if (wakeLock?.isHeld == true) wakeLock.release()
                    }
                    runCatching { pending.finish() }
                }
            }
            Handler(Looper.getMainLooper()).postDelayed({ finishSafely() }, 8500L)
            AlertSoundHelper.playLessonBoundaryAlert(
                context = context,
                boundaryType = boundaryType,
                spokenText = spokenText,
                voiceEnabled = voiceEnabled,
                volumePercent = volumePercent,
                onComplete = { finishSafely() }
            )
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_PLAY_SOUND = "extra_play_sound"
        const val EXTRA_BOUNDARY_TYPE = "extra_boundary_type"
        const val EXTRA_SPOKEN_TEXT = "extra_spoken_text"
        const val EXTRA_VOICE_ENABLED = "extra_voice_enabled"
        const val EXTRA_VOLUME_PERCENT = "extra_volume_percent"
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) {
            val state = JsonStorageRepository(context).loadState()
            LessonAlarmScheduler.rescheduleAll(context, state)
        }
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "teacher_ledger_alerts_v3"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "تنبيهات الدروس والأذكار والصلاة",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات دقيقة تعمل حتى عند إغلاق التطبيق"
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun showNotification(context: Context, id: Int, title: String, body: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
