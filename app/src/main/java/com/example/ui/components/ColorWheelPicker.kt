package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LocalExtendedColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class ColorCustomizationTarget(
    val id: String,
    val titleAr: String,
    val subtitleAr: String
) {
    TEXT(
        id = "text",
        titleAr = "النصوص",
        subtitleAr = "لون النصوص والعناوين في التطبيق"
    ),
    BUTTONS(
        id = "buttons",
        titleAr = "الأزرار",
        subtitleAr = "لون الأزرار الرئيسية والأيقونات البارزة"
    ),
    BACKGROUND(
        id = "background",
        titleAr = "الخلفية",
        subtitleAr = "لون خلفية الشاشات والتدرج العام"
    ),
    CARDS_AND_DATA(
        id = "cards_data",
        titleAr = "البيانات والخاصة",
        subtitleAr = "لون البطاقات والبيانات والجداول والأمور الخاصة"
    )
}

fun colorToHexLong(color: Color): Long {
    val argb = color.toArgb()
    return (argb.toLong() and 0xFFFFFFFFL) or 0xFF000000L
}

fun formatColorHex(hex: Long): String {
    if (hex == 0L) return "تلقائي"
    val rgb = (hex and 0xFFFFFFL).toInt()
    return String.format("#%06X", rgb)
}

fun colorHexToHsv(hex: Long, fallbackHue: Float = 200f): FloatArray {
    if (hex == 0L) {
        return floatArrayOf(fallbackHue, 0.75f, 0.92f)
    }
    val r = ((hex shr 16) and 0xFF) / 255f
    val g = ((hex shr 8) and 0xFF) / 255f
    val b = (hex and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, sat, max)
}

fun hsvToHexLong(hue: Float, sat: Float, value: Float): Long {
    val c = Color.hsv(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = sat.coerceIn(0f, 1f),
        value = value.coerceIn(0.05f, 1f)
    )
    return colorToHexLong(c)
}

private val QuickWheelColors = listOf(
    "افتراضي" to 0L,
    "كحلي ليلي" to 0xFF061124L,
    "أسود فاحم" to 0xFF000000L,
    "أبيض نقي" to 0xFFFFFFFFL,
    "ذهبي" to 0xFFFFC83DL,
    "تركوازي" to 0xFF22D3EEL,
    "نعناعي" to 0xFF02C39AL,
    "أزرق ملكي" to 0xFF1D6FE8L,
    "بنفسجي" to 0xFF7C3AEDL,
    "وردي" to 0xFFF472B6L,
    "برتقالي" to 0xFFFB923CL
)

@Composable
fun InteractiveColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHueSaturationChanged: (Float, Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }
    val previewColor = remember(hue, saturation, brightness) {
        Color(hsvToHexLong(hue, saturation, brightness))
    }

    fun updateFromOffset(offset: Offset) {
        if (wheelSize.width <= 0 || wheelSize.height <= 0) return
        val cx = wheelSize.width / 2f
        val cy = wheelSize.height / 2f
        val radius = min(cx, cy)
        if (radius <= 1f) return
        val dx = offset.x - cx
        val dy = offset.y - cy
        val dist = hypot(dx, dy).coerceAtMost(radius)
        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        var deg = Math.toDegrees(angleRad).toFloat()
        if (deg < 0f) deg += 360f
        val sat = (dist / radius).coerceIn(0f, 1f)
        onHueSaturationChanged(deg, sat)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(216.dp)
                .onSizeChanged { wheelSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> updateFromOffset(offset) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateFromOffset(change.position)
                    }
                }
                .testTag("interactive_color_wheel_canvas"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val hueColors = listOf(
                    Color(0xFFFF0000),
                    Color(0xFFFFFF00),
                    Color(0xFF00FF00),
                    Color(0xFF00FFFF),
                    Color(0xFF0000FF),
                    Color(0xFFFF00FF),
                    Color(0xFFFF0000)
                )
                drawCircle(
                    brush = Brush.sweepGradient(colors = hueColors, center = center),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                if (brightness < 0.99f) {
                    drawCircle(
                        color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.88f)),
                        radius = radius,
                        center = center
                    )
                }

                val angleRad = hue * (PI / 180.0)
                val selectorDist = saturation.coerceIn(0f, 1f) * (radius - 6f)
                val sx = center.x + (cos(angleRad) * selectorDist).toFloat()
                val sy = center.y + (sin(angleRad) * selectorDist).toFloat()
                drawCircle(
                    color = previewColor,
                    radius = 14f,
                    center = Offset(sx, sy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 16f,
                    center = Offset(sx, sy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = 18f,
                    center = Offset(sx, sy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "درجة السطوع والإضاءة (${(brightness * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        text = formatColorHex(colorToHexLong(previewColor)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChanged,
                valueRange = 0.06f..1.0f,
                modifier = Modifier.testTag("color_wheel_brightness_slider")
            )
        }
    }
}

@Composable
fun ColorWheelCustomizerSection(
    customColorsEnabled: Boolean,
    customTextColorHex: Long,
    customButtonColorHex: Long,
    customBgColorHex: Long,
    customCardAccentColorHex: Long,
    onApplyColors: (
        enabled: Boolean,
        textHex: Long,
        buttonHex: Long,
        bgHex: Long,
        cardAccentHex: Long
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    var selectedTarget by remember { mutableStateOf(ColorCustomizationTarget.BUTTONS) }

    fun hexFor(target: ColorCustomizationTarget): Long = when (target) {
        ColorCustomizationTarget.TEXT -> customTextColorHex
        ColorCustomizationTarget.BUTTONS -> customButtonColorHex
        ColorCustomizationTarget.BACKGROUND -> customBgColorHex
        ColorCustomizationTarget.CARDS_AND_DATA -> customCardAccentColorHex
    }

    val currentTargetHex = hexFor(selectedTarget)
    val initialHsv = remember(selectedTarget, currentTargetHex) {
        val fallbackColor = when (selectedTarget) {
            ColorCustomizationTarget.TEXT -> Color.White
            ColorCustomizationTarget.BUTTONS -> Color(0xFFFFC83D)
            ColorCustomizationTarget.BACKGROUND -> Color(0xFF061124)
            ColorCustomizationTarget.CARDS_AND_DATA -> Color(0xFF22D3EE)
        }
        colorHexToHsv(if (currentTargetHex != 0L) currentTargetHex else colorToHexLong(fallbackColor))
    }

    var hue by remember(selectedTarget, currentTargetHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(selectedTarget, currentTargetHex) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(selectedTarget, currentTargetHex) { mutableFloatStateOf(initialHsv[2]) }

    fun commitTargetHex(newHex: Long) {
        val nextText = if (selectedTarget == ColorCustomizationTarget.TEXT) newHex else customTextColorHex
        val nextBtn = if (selectedTarget == ColorCustomizationTarget.BUTTONS) newHex else customButtonColorHex
        val nextBg = if (selectedTarget == ColorCustomizationTarget.BACKGROUND) newHex else customBgColorHex
        val nextCard = if (selectedTarget == ColorCustomizationTarget.CARDS_AND_DATA) newHex else customCardAccentColorHex
        val anyActive = nextText != 0L || nextBtn != 0L || nextBg != 0L || nextCard != 0L
        onApplyColors(anyActive, nextText, nextBtn, nextBg, nextCard)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "اختر أي عنصر ثم حرّك إصبعك على عجلة الألوان لتخصيصه مباشرة:",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textMuted
        )

        // 4 Clear Target Cards in 2x2 Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorCustomizationTarget.entries.chunked(2).forEach { rowTargets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTargets.forEach { target ->
                        val hex = hexFor(target)
                        val isSelected = selectedTarget == target
                        val dotColor = when {
                            hex != 0L -> Color(hex)
                            target == ColorCustomizationTarget.TEXT -> MaterialTheme.colorScheme.onSurface
                            target == ColorCustomizationTarget.BUTTONS -> MaterialTheme.colorScheme.primary
                            target == ColorCustomizationTarget.BACKGROUND -> MaterialTheme.colorScheme.background
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else ext.cardSecondary,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else ext.borderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedTarget = target }
                                .testTag("color_wheel_target_${target.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(1.2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.titleAr,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatColorHex(hex),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.textMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "العنصر المحدد: ${selectedTarget.titleAr} — ${selectedTarget.subtitleAr}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        InteractiveColorWheel(
            hue = hue,
            saturation = saturation,
            brightness = brightness,
            onHueSaturationChanged = { h, s ->
                hue = h
                saturation = s
                commitTargetHex(hsvToHexLong(h, s, brightness))
            },
            onBrightnessChanged = { b ->
                brightness = b
                commitTargetHex(hsvToHexLong(hue, saturation, b))
            }
        )

        // Quick Swatch Dots (incl. Auto Reset, Black, White)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickWheelColors.forEach { (label, hex) ->
                val isSelected = currentTargetHex == hex
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (hex == 0L) {
                            commitTargetHex(0L)
                        } else {
                            val hsv = colorHexToHsv(hex)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            commitTargetHex(hex)
                        }
                    },
                    leadingIcon = {
                        if (hex == 0L) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }
                    },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        // Live Preview Card showing Text, Button, Background, and Card/Data colors together
        val previewBg = if (customBgColorHex != 0L) Color(customBgColorHex) else MaterialTheme.colorScheme.background
        val previewText = if (customTextColorHex != 0L) Color(customTextColorHex) else MaterialTheme.colorScheme.onSurface
        val previewBtn = if (customButtonColorHex != 0L) Color(customButtonColorHex) else MaterialTheme.colorScheme.primary
        val previewData = if (customCardAccentColorHex != 0L) Color(customCardAccentColorHex) else MaterialTheme.colorScheme.secondary
        val previewBtnContent = if (previewBtn.luminance() > 0.52f) Color(0xFF061124) else Color.White

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = previewBg,
            border = BorderStroke(1.5.dp, previewData),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "معاينة فورية للألوان",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = previewText
                    )
                    Text(
                        text = "البيانات والإطارات • النصوص والخلفيات",
                        style = MaterialTheme.typography.labelSmall,
                        color = previewData
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = previewBtn
                ) {
                    Text(
                        text = "زر تجريبي",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = previewBtnContent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        if (customColorsEnabled) {
            OutlinedButton(
                onClick = { onApplyColors(false, 0L, 0L, 0L, 0L) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reset_all_wheel_colors_btn")
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("استعادة الألوان الافتراضية للتطبيق")
            }
        }
    }
}

@Composable
fun ColorWheelPickerDialog(
    title: String,
    initialHex: Long,
    onSelectHex: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val startHsv = remember(initialHex) {
        colorHexToHsv(if (initialHex != 0L) initialHex else 0xFF22D3EEL)
    }
    var hue by remember(initialHex) { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(startHsv[1]) }
    var brightness by remember(initialHex) { mutableFloatStateOf(startHsv[2]) }
    val selectedHex = hsvToHexLong(hue, saturation, brightness)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                InteractiveColorWheel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onHueSaturationChanged = { h, s ->
                        hue = h
                        saturation = s
                    },
                    onBrightnessChanged = { b -> brightness = b }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            onSelectHex(0L)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("افتراضي")
                    }
                    Button(
                        onClick = {
                            onSelectHex(selectedHex)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("تطبيق اللون", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
