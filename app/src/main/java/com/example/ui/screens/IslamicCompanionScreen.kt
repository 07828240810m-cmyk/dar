package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OfflineQuranData
import com.example.model.AppState
import com.example.model.PrayerLocationSettings
import com.example.model.TeacherProfile
import com.example.ui.components.neonCardGlow
import com.example.ui.components.neonTapFeedback
import com.example.ui.components.rememberNeonTapController
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LocalExtendedColors
import com.example.utils.FloatingDhikrOverlayManager
import com.example.utils.HapticFeedbackHelper
import com.example.utils.OfflinePrayerCalculator

private val ayahHighlightColors = listOf(
    0x33FFD54FL to "ذهبي",
    0x3340D6B4L to "زمردي",
    0x3354A0FFL to "سماوي",
    0x33FF6B81L to "وردي",
    0x33A55EEAL to "بنفسجي"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicCompanionScreen(
    state: AppState,
    onUpdateProfile: (TeacherProfile) -> Unit,
    onUpdateQuranPosition: (Int, Int) -> Unit,
    onToggleAyahColor: (Int, Int, Long?) -> Unit,
    onUpdatePrayerSettings: (PrayerLocationSettings) -> Unit,
    onDetectGpsLocation: () -> PrayerLocationSettings?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val p = state.profile
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("القرآن الكريم", "الأذكار والتسبيح", "مواقيت الصلاة", "النافذة العائمة")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("islamic_companion_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("القرآن والأذكار ومواقيت الصلاة", fontWeight = FontWeight.Bold)
                    Text(
                        "يعمل بدون إنترنت • آخر موضع: سورة ${
                            OfflineQuranData.surahs.find { it.id == p.quranLastSurahId }?.nameArabic ?: "الفاتحة"
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("islamic_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            }
        )

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        when (selectedTab) {
            0 -> MushafQuranReaderSection(
                profile = p,
                highlightColors = ayahHighlightColors,
                onSavePosition = onUpdateQuranPosition,
                onToggleAyahColor = onToggleAyahColor
            )
            1 -> AthkarAndTasbeehSection(
                profile = p,
                onUpdateProfile = onUpdateProfile
            )
            2 -> OfflinePrayerTimesSection(
                settings = p.prayerSettings,
                onUpdateSettings = onUpdatePrayerSettings,
                onDetectGps = onDetectGpsLocation
            )
            else -> FloatingDhikrSettingsSection(
                profile = p,
                onUpdateProfile = onUpdateProfile
            )
        }
    }
}

@Composable
private fun AthkarAndTasbeehSection(
    profile: TeacherProfile,
    onUpdateProfile: (TeacherProfile) -> Unit
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var tasbeehCount by remember { mutableStateOf(0) }
    var selectedPresetIdx by remember { mutableStateOf(0) }
    var athkarTab by remember { mutableStateOf(0) }

    val list = if (athkarTab == 0) OfflineQuranData.morningAthkar else OfflineQuranData.eveningAthkar

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val tasbeehTap = rememberNeonTapController()
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("المسبحة الإلكترونية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = OfflineQuranData.tasbeehPresets[selectedPresetIdx],
                        fontFamily = AmiriFontFamily,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(116.dp)
                            .neonTapFeedback(
                                controller = tasbeehTap,
                                glowColor = MaterialTheme.colorScheme.primary,
                                cornerRadius = 58.dp
                            )
                            .clickable {
                                tasbeehTap.trigger()
                                tasbeehCount++
                                HapticFeedbackHelper.pulseSaveSuccess(context)
                            }
                            .testTag("tasbeeh_tap_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$tasbeehCount",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { tasbeehCount = 0 }) { Text("تصفير") }
                        FilledTonalButton(onClick = {
                            selectedPresetIdx = (selectedPresetIdx + 1) % OfflineQuranData.tasbeehPresets.size
                            tasbeehCount = 0
                        }) { Text("تغيير الذكر") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إشعارات الأذكار اليومية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تنبيه أذكار الصباح")
                            Text(
                                String.format("الساعة %02d:%02d ص", profile.morningAthkarHour, profile.morningAthkarMinute),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.morningAthkarNotifyEnabled,
                            onCheckedChange = {
                                onUpdateProfile(profile.copy(morningAthkarNotifyEnabled = it))
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تنبيه أذكار المساء")
                            Text(
                                String.format("الساعة %02d:%02d م", (profile.eveningAthkarHour - 12).coerceAtLeast(1), profile.eveningAthkarMinute),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.eveningAthkarNotifyEnabled,
                            onCheckedChange = {
                                onUpdateProfile(profile.copy(eveningAthkarNotifyEnabled = it))
                            }
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = athkarTab == 0, onClick = { athkarTab = 0 }, label = { Text("أذكار الصباح") })
                FilterChip(selected = athkarTab == 1, onClick = { athkarTab = 1 }, label = { Text("أذكار المساء") })
            }
        }

        items(list, key = { it.id }) { item ->
            var remaining by remember(item.id) { mutableStateOf(item.repeatCount) }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (remaining > 0) {
                            remaining--
                            HapticFeedbackHelper.pulseSaveSuccess(context)
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (remaining == 0) Color(0xFF2ED573) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                if (remaining == 0) "تم ✓" else "التكرار: $remaining",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        item.arabicText,
                        fontFamily = AmiriFontFamily,
                        fontSize = 19.sp,
                        lineHeight = 32.sp
                    )
                    if (item.virtue.isNotBlank()) {
                        Text(item.virtue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePrayerTimesSection(
    settings: PrayerLocationSettings,
    onUpdateSettings: (PrayerLocationSettings) -> Unit,
    onDetectGps: () -> PrayerLocationSettings?
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    val schedule = remember(settings) { OfflinePrayerCalculator.computeSchedule(settings) }
    var showCityMenu by remember { mutableStateOf(false) }

    val locLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val updated = onDetectGps()
            if (updated != null) {
                Toast.makeText(context, "تم تحديد الموقع: ${updated.cityName}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "تعذر قراءة GPS حالياً؛ اختر مدينتك من القائمة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 22.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Mosque, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(schedule.cityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "الصلاة القادمة: ${schedule.nextPrayerTitle} • ${schedule.nextPrayerRemainingText}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                locLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحديد GPS")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showCityMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("اختيار المدينة")
                            }
                            DropdownMenu(expanded = showCityMenu, onDismissRequest = { showCityMenu = false }) {
                                OfflinePrayerCalculator.iraqiCities.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.nameAr) },
                                        onClick = {
                                            onUpdateSettings(
                                                settings.copy(
                                                    cityName = preset.nameAr,
                                                    latitude = preset.latitude,
                                                    longitude = preset.longitude
                                                )
                                            )
                                            showCityMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        items(schedule.prayers) { slot ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("صلاة ${slot.titleAr}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(slot.formattedArabic(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (slot.id != "sunrise") {
                        Switch(
                            checked = slot.notifyEnabled,
                            onCheckedChange = { enabled ->
                                val next = when (slot.id) {
                                    "fajr" -> settings.copy(fajrNotifyEnabled = enabled)
                                    "dhuhr" -> settings.copy(dhuhrNotifyEnabled = enabled)
                                    "asr" -> settings.copy(asrNotifyEnabled = enabled)
                                    "maghrib" -> settings.copy(maghribNotifyEnabled = enabled)
                                    "isha" -> settings.copy(ishaNotifyEnabled = enabled)
                                    else -> settings
                                }
                                onUpdateSettings(next)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDhikrSettingsSection(
    profile: TeacherProfile,
    onUpdateProfile: (TeacherProfile) -> Unit
) {
    val ext = LocalExtendedColors.current
    val previewTap = rememberNeonTapController()
    val context = LocalContext.current
    var customText by remember(profile.floatingDhikrCustomText) { mutableStateOf(profile.floatingDhikrCustomText) }
    val hasOverlayPerm = FloatingDhikrOverlayManager.canDrawOverlays(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("النافذة المنبثقة الإيمانية (خارج التطبيق)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "تظهر نافذة عائمة أنيقة بخلفية بيضاء وحواف منحنية بإطار أخضر غامق وخط عربي أسود مزخرف، مع إمكانية سحبها وتحريكها فوق التطبيقات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasOverlayPerm) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تفعيل إذن الظهور فوق التطبيقات")
                        }
                    } else {
                        Text("✓ إذن الظهور فوق التطبيقات مفعّل", color = Color(0xFF2ED573), fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("تفعيل الظهور التلقائي الدوري", modifier = Modifier.weight(1f))
                        Switch(
                            checked = profile.floatingDhikrEnabled,
                            onCheckedChange = {
                                onUpdateProfile(profile.copy(floatingDhikrEnabled = it, floatingDhikrCustomText = customText))
                            }
                        )
                    }

                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("الذكر أو الآية المعروضة") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120).forEach { mins ->
                            FilterChip(
                                selected = profile.floatingDhikrIntervalMinutes == mins,
                                onClick = {
                                    onUpdateProfile(
                                        profile.copy(
                                            floatingDhikrIntervalMinutes = mins,
                                            floatingDhikrCustomText = customText
                                        )
                                    )
                                },
                                label = { Text("كل $mins د") }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            previewTap.trigger()
                            onUpdateProfile(profile.copy(floatingDhikrCustomText = customText))
                            FloatingDhikrOverlayManager.showDhikrWindow(
                                context = context,
                                title = "النافذة الإيمانية",
                                dhikrText = customText.ifBlank { profile.floatingDhikrCustomText }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonTapFeedback(
                                controller = previewTap,
                                glowColor = MaterialTheme.colorScheme.primary,
                                cornerRadius = 24.dp
                            )
                            .testTag("preview_floating_dhikr_btn")
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ ومعاينة النافذة المنبثقة الآن")
                    }
                }
            }
        }
    }
}
