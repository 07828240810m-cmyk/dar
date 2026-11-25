package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R

object FloatingDhikrOverlayManager {

    private var activeView: View? = null

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun showDhikrWindow(context: Context, title: String, dhikrText: String) {
        if (!canDrawOverlays(context)) {
            NotificationHelper.showNotification(
                context = context,
                id = 8840,
                title = title,
                body = dhikrText
            )
            return
        }

        Handler(Looper.getMainLooper()).post {
            dismiss(context)
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = context.resources.displayMetrics.density
                val pad = (18 * density).toInt()
                val darkGreenBorder = Color.parseColor("#0D5C2E")

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, (14 * density).toInt(), pad, pad)
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = 28f * density
                        setStroke((2.8f * density).toInt(), darkGreenBorder)
                    }
                    elevation = 10f * density
                }

                val calligraphyFont = runCatching {
                    ResourcesCompat.getFont(context, R.font.amiri)
                }.getOrNull() ?: Typeface.SERIF

                val dragHintTv = TextView(context).apply {
                    text = "⋮⋮ اسحب لتحريك النافذة ⋮⋮"
                    setTextColor(darkGreenBorder)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, (4 * density).toInt())
                }

                val titleTv = TextView(context).apply {
                    text = title
                    setTextColor(darkGreenBorder)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }

                val bodyTv = TextView(context).apply {
                    text = dhikrText
                    setTextColor(Color.BLACK)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = calligraphyFont
                    gravity = Gravity.CENTER
                    setPadding(0, (8 * density).toInt(), 0, (14 * density).toInt())
                    setLineSpacing(6f * density, 1.15f)
                }

                val closeBtn = Button(context).apply {
                    text = "إغلاق ✕"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    background = GradientDrawable().apply {
                        setColor(darkGreenBorder)
                        cornerRadius = 20f * density
                    }
                    setPadding((22 * density).toInt(), (6 * density).toInt(), (22 * density).toInt(), (6 * density).toInt())
                    setOnClickListener { dismiss(context) }
                }

                container.addView(dragHintTv)
                container.addView(titleTv)
                container.addView(bodyTv)
                container.addView(closeBtn)

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val screenWidth = context.resources.displayMetrics.widthPixels
                val windowWidth = (screenWidth * 0.88f).toInt()
                val params = WindowManager.LayoutParams(
                    windowWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = ((screenWidth - windowWidth) / 2).coerceAtLeast(0)
                    y = (80 * density).toInt()
                }

                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f
                container.setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            runCatching { wm.updateViewLayout(container, params) }
                            true
                        }
                        else -> false
                    }
                }

                wm.addView(container, params)
                activeView = container
                Handler(Looper.getMainLooper()).postDelayed({ dismiss(context) }, 30_000L)
            } catch (_: Exception) {
                NotificationHelper.showNotification(context, 8840, title, dhikrText)
            }
        }
    }

    fun dismiss(context: Context) {
        val v = activeView ?: return
        activeView = null
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(v)
        } catch (_: Exception) {
        }
    }
}

class FloatingDhikrReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_DHIKR_TEXT)
            ?: "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ"
        val title = intent.getStringExtra(EXTRA_DHIKR_TITLE) ?: "النافذة الإيمانية"
        FloatingDhikrOverlayManager.showDhikrWindow(context.applicationContext, title, text)
    }

    companion object {
        const val EXTRA_DHIKR_TITLE = "extra_dhikr_title"
        const val EXTRA_DHIKR_TEXT = "extra_dhikr_text"
    }
}
