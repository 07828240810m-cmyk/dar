package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MushafPage
import com.example.data.MushafPageSegment
import com.example.data.OfflineQuranData
import com.example.data.OfflineQuranTafsirHelper
import com.example.data.QuranAyah
import com.example.data.QuranSurah
import com.example.model.TeacherProfile
import com.example.ui.components.neonCardGlow
import com.example.ui.components.neonTapFeedback
import com.example.ui.components.rememberNeonTapController
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LocalExtendedColors
import com.example.utils.PageTurnSoundHelper
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class MushafPaperTheme(
    val titleAr: String,
    val pageBg: Color,
    val textColor: Color,
    val ornamentColor: Color,
    val bannerBg: Color
) {
    ANTIQUE(
        titleAr = "عتيق",
        pageBg = Color(0xFFFAF1DC),
        textColor = Color(0xFF1D160B),
        ornamentColor = Color(0xFF8C6214),
        bannerBg = Color(0xFFEFE0BB)
    ),
    WHITE(
        titleAr = "أبيض",
        pageBg = Color(0xFFFFFFFF),
        textColor = Color(0xFF0F172A),
        ornamentColor = Color(0xFF0D5C2E),
        bannerBg = Color(0xFFEBF7F0)
    ),
    NIGHT(
        titleAr = "ليلي",
        pageBg = Color(0xFF0B1420),
        textColor = Color(0xFFF3EFE6),
        ornamentColor = Color(0xFFE9C46A),
        bannerBg = Color(0xFF132236)
    )
}

@Composable
fun MushafQuranReaderSection(
    profile: TeacherProfile,
    highlightColors: List<Pair<Long, String>>,
    onSavePosition: (Int, Int) -> Unit,
    onToggleAyahColor: (Int, Int, Long?) -> Unit
) {
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()
    val pages = remember { OfflineQuranData.buildMushafPages() }
    val initialBookmarkPage = remember(profile.quranLastSurahId, profile.quranLastAyahNumber, pages) {
        OfflineQuranData.pageIndexFor(profile.quranLastSurahId, profile.quranLastAyahNumber, pages)
    }

    var showCover by remember { mutableStateOf(true) }
    var paperTheme by remember { mutableStateOf(MushafPaperTheme.ANTIQUE) }
    var fontSizeSp by remember { mutableStateOf(23f) }
    var soundEnabled by remember { mutableStateOf(true) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showIndexDialog by remember { mutableStateOf(false) }
    var inspectedAyah by remember { mutableStateOf<Pair<QuranSurah, QuranAyah>?>(null) }

    val pagerState = rememberPagerState(
        initialPage = initialBookmarkPage,
        pageCount = { pages.size }
    )

    var lastSettled by remember { mutableStateOf(initialBookmarkPage) }
    LaunchedEffect(pagerState.settledPage, showCover) {
        if (!showCover && pagerState.settledPage != lastSettled) {
            lastSettled = pagerState.settledPage
            PageTurnSoundHelper.playPageTurn(soundEnabled)
        }
    }

    if (showCover) {
        MushafCoverView(
            profile = profile,
            totalPages = pages.size,
            onResumeReading = {
                scope.launch {
                    val target = OfflineQuranData.pageIndexFor(profile.quranLastSurahId, profile.quranLastAyahNumber, pages)
                    showCover = false
                    lastSettled = target
                    pagerState.scrollToPage(target)
                    PageTurnSoundHelper.playPageTurn(soundEnabled)
                }
            },
            onOpenFromStart = {
                scope.launch {
                    showCover = false
                    lastSettled = 0
                    pagerState.scrollToPage(0)
                    PageTurnSoundHelper.playPageTurn(soundEnabled)
                }
            },
            onOpenIndex = { showIndexDialog = true }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Mushaf Toolbar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 14.dp, alphaFactor = 0.20f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { showCover = true },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("الغلاف", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("mushaf_cover_btn")
                    )
                    AssistChip(
                        onClick = { showIndexDialog = true },
                        leadingIcon = { Icon(Icons.Filled.FormatListNumbered, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("السور (${OfflineQuranData.surahs.size})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("mushaf_index_btn")
                    )
                    MushafPaperTheme.entries.forEach { pt ->
                        FilterChip(
                            selected = pt == paperTheme,
                            onClick = { paperTheme = pt },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(pt.pageBg)
                                        .border(1.dp, pt.ornamentColor, CircleShape)
                                )
                            },
                            label = { Text(pt.titleAr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    OutlinedIconButton(
                        onClick = { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(18f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "تصغير الخط", modifier = Modifier.size(16.dp))
                    }
                    OutlinedIconButton(
                        onClick = { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(32f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "تكبير الخط", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { soundEnabled = !soundEnabled },
                        modifier = Modifier.size(36.dp).testTag("mushaf_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (soundEnabled) "إيقاف صوت التقليب" else "تشغيل صوت التقليب",
                            tint = if (soundEnabled) MaterialTheme.colorScheme.primary else ext.textMuted
                        )
                    }
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.size(36.dp).testTag("mushaf_search_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "بحث في الآيات",
                            tint = if (showSearchBar) MaterialTheme.colorScheme.primary else ext.textMuted
                        )
                    }
                }
            }

            if (showSearchBar) {
                val results = remember(searchQuery) { OfflineQuranData.searchAyahs(searchQuery) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث في آيات المصحف (بدون تشكيل)...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("mushaf_search_input")
                    )
                    if (searchQuery.trim().length >= 2) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(results) { (surah, ayah) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ext.cardElevated,
                                    border = BorderStroke(1.dp, ext.borderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetPage = OfflineQuranData.pageIndexFor(surah.id, ayah.numberInSurah, pages)
                                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                                            inspectedAyah = surah to ayah
                                            showSearchBar = false
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "${surah.nameArabic} • آية ${OfflineQuranData.toArabicIndicDigits(ayah.numberInSurah)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${ayah.textUthmani} ﴿${OfflineQuranData.toArabicIndicDigits(ayah.numberInSurah)}﴾",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 18.sp,
                                            lineHeight = 30.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Realistic Horizontal Page-Flip Mushaf Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("mushaf_horizontal_pager")
            ) { pageIndex ->
                val page = pages[pageIndex]
                val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)

                MushafBookPageCard(
                    page = page,
                    totalPages = pages.size,
                    paperTheme = paperTheme,
                    fontSizeSp = fontSizeSp,
                    profile = profile,
                    onTapAyah = { surah, ayah ->
                        inspectedAyah = surah to ayah
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 18f * density
                            val absOff = pageOffset.absoluteValue
                            val scale = 1f - (absOff * 0.06f)
                            scaleX = scale
                            scaleY = scale
                            rotationY = pageOffset * 26f
                            transformOrigin = if (pageOffset > 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                            alpha = 1f - (absOff * 0.25f)
                        }
                )
            }

            // Bottom Book Navigation Strip
            val currentPageObj = pages.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = pagerState.currentPage > 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("mushaf_prev_page_btn")
                ) {
                    Text("الصفحة السابقة")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "صفحة ${OfflineQuranData.toArabicIndicDigits((currentPageObj?.pageNumber ?: 1))} من ${OfflineQuranData.toArabicIndicDigits(pages.size)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "انقر على أي آية للتفسير والمعاني",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textMuted
                    )
                }

                Button(
                    enabled = pagerState.currentPage < pages.lastIndex,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pages.lastIndex))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("mushaf_next_page_btn")
                ) {
                    Text("الصفحة التالية")
                }
            }
        }
    }

    // Surah Index Dialog
    if (showIndexDialog) {
        var idxSearch by remember { mutableStateOf("") }
        val filteredSurahs = remember(idxSearch) {
            val norm = OfflineQuranData.stripArabicTashkeel(idxSearch)
            if (norm.isEmpty()) OfflineQuranData.surahs
            else OfflineQuranData.surahs.filter {
                OfflineQuranData.stripArabicTashkeel(it.nameArabic).contains(norm, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showIndexDialog = false },
            title = { Text("فهرس سور المصحف الشريف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = idxSearch,
                        onValueChange = { idxSearch = it },
                        placeholder = { Text("ابحث عن اسم السورة...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredSurahs, key = { it.id }) { surah ->
                            val targetPage = OfflineQuranData.pageIndexFor(surah.id, surah.ayahs.first().numberInSurah, pages)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ext.cardElevated,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showIndexDialog = false
                                        scope.launch {
                                            showCover = false
                                            lastSettled = targetPage
                                            pagerState.scrollToPage(targetPage)
                                            PageTurnSoundHelper.playPageTurn(soundEnabled)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${OfflineQuranData.toArabicIndicDigits(surah.id)}. ${surah.nameArabic}",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${surah.revelationType} • ${surah.ayahs.size} آية • ${OfflineQuranData.juzLabelForSurah(surah.id)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.textMuted
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "ص ${OfflineQuranData.toArabicIndicDigits(targetPage + 1)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIndexDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Interactive Ayah Tafsir & Word Meanings Dialog
    inspectedAyah?.let { (surah, ayah) ->
        AyahTafsirAndActionsDialog(
            surah = surah,
            ayah = ayah,
            profile = profile,
            highlightColors = highlightColors,
            onBookmarkHere = {
                onSavePosition(surah.id, ayah.numberInSurah)
                inspectedAyah = null
            },
            onSetHighlightColor = { colorHex ->
                onToggleAyahColor(surah.id, ayah.numberInSurah, colorHex)
            },
            onDismiss = { inspectedAyah = null }
        )
    }
}

@Composable
private fun MushafCoverView(
    profile: TeacherProfile,
    totalPages: Int,
    onResumeReading: () -> Unit,
    onOpenFromStart: () -> Unit,
    onOpenIndex: () -> Unit
) {
    val lastSurah = remember(profile.quranLastSurahId) {
        OfflineQuranData.surahs.find { it.id == profile.quranLastSurahId } ?: OfflineQuranData.surahs.first()
    }
    val openTap = rememberNeonTapController()
    val resumeTap = rememberNeonTapController()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp)
                .neonCardGlow(Color(0xFFD4AF37), true, 24.dp, alphaFactor = 0.45f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A3A2A), Color(0xFF06251B), Color(0xFF041711))
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val gold = Color(0xFFE5C158)
                    drawRoundRect(
                        color = gold.copy(alpha = 0.85f),
                        topLeft = Offset(8f, 8f),
                        size = Size(size.width - 16f, size.height - 16f),
                        cornerRadius = CornerRadius(32f, 32f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = gold.copy(alpha = 0.45f),
                        topLeft = Offset(22f, 22f),
                        size = Size(size.width - 44f, size.height - 44f),
                        cornerRadius = CornerRadius(24f, 24f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    val cx = size.width / 2f
                    val cy = size.height * 0.42f
                    drawCircle(
                        color = gold.copy(alpha = 0.14f),
                        radius = size.minDimension * 0.32f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = gold.copy(alpha = 0.55f),
                        radius = size.minDimension * 0.32f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "﴿ إِنَّهُ لَقُرْآنٌ كَرِيمٌ ﴾",
                        fontFamily = AmiriFontFamily,
                        fontSize = 18.sp,
                        color = Color(0xFFE9C46A),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "المصحف الشريف",
                        fontFamily = AmiriFontFamily,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFE599),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "مصحف متكامل بتقليب الصفحات • التفسير الميسر ومعاني الكلمات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD1FAE5),
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE9C46A).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFE9C46A).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${OfflineQuranData.surahs.size} سورة • ${OfflineQuranData.toArabicIndicDigits(totalPages)} صفحة • يعمل أوفلاين",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFE599),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "آخر موضع: ${lastSurah.nameArabic} • آية ${OfflineQuranData.toArabicIndicDigits(profile.quranLastAyahNumber)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { resumeTap.trigger(onResumeReading) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D5C2E),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .neonTapFeedback(resumeTap, Color(0xFF02C39A), 14.dp)
                    .testTag("mushaf_resume_btn")
            ) {
                Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("متابعة القراءة", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { openTap.trigger(onOpenFromStart) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .neonTapFeedback(openTap, MaterialTheme.colorScheme.primary, 14.dp)
                    .testTag("mushaf_open_start_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("فتح المصحف", fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onOpenIndex,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Filled.FormatListNumbered, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("تصفح فهرس السور (${OfflineQuranData.surahs.size} سورة • جزء عمّ والملك والفاتحة والكهف)")
        }
    }
}

@Composable
private fun MushafBookPageCard(
    page: MushafPage,
    totalPages: Int,
    paperTheme: MushafPaperTheme,
    fontSizeSp: Float,
    profile: TeacherProfile,
    onTapAyah: (QuranSurah, QuranAyah) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = paperTheme.pageBg,
        shadowElevation = 8.dp,
        border = BorderStroke(2.dp, paperTheme.ornamentColor.copy(alpha = 0.72f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(1.dp, paperTheme.ornamentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Running Book Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = page.primarySurahName,
                        fontFamily = AmiriFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = paperTheme.ornamentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = page.juzLabel,
                        fontFamily = AmiriFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = paperTheme.ornamentColor
                    )
                }
                HorizontalDivider(color = paperTheme.ornamentColor.copy(alpha = 0.35f))

                // Page Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    page.segments.forEach { segment ->
                        MushafSegmentBlock(
                            segment = segment,
                            paperTheme = paperTheme,
                            fontSizeSp = fontSizeSp,
                            profile = profile,
                            onTapAyah = onTapAyah
                        )
                    }
                }

                // Page Footer
                HorizontalDivider(color = paperTheme.ornamentColor.copy(alpha = 0.35f))
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = paperTheme.bannerBg,
                        border = BorderStroke(1.dp, paperTheme.ornamentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "ـ ${OfflineQuranData.toArabicIndicDigits(page.pageNumber)} / ${OfflineQuranData.toArabicIndicDigits(totalPages)} ـ",
                            fontFamily = AmiriFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = paperTheme.ornamentColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MushafSegmentBlock(
    segment: MushafPageSegment,
    paperTheme: MushafPaperTheme,
    fontSizeSp: Float,
    profile: TeacherProfile,
    onTapAyah: (QuranSurah, QuranAyah) -> Unit
) {
    val surah = segment.surah
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (segment.showSurahHeader) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = paperTheme.bannerBg,
                border = BorderStroke(1.3.dp, paperTheme.ornamentColor.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = surah.revelationType,
                        style = MaterialTheme.typography.labelSmall,
                        color = paperTheme.ornamentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = surah.nameArabic,
                        fontFamily = AmiriFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = paperTheme.textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "آياتها ${OfflineQuranData.toArabicIndicDigits(surah.ayahs.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = paperTheme.ornamentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (segment.showBismillah) {
            Text(
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                fontFamily = AmiriFontFamily,
                fontSize = (fontSizeSp - 1f).sp,
                fontWeight = FontWeight.Bold,
                color = paperTheme.ornamentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
        }

        val annotatedPageText = remember(
            segment,
            profile.quranColoredAyahs,
            profile.quranLastSurahId,
            profile.quranLastAyahNumber,
            paperTheme
        ) {
            buildAnnotatedString {
                segment.ayahs.forEach { ayah ->
                    val key = "${surah.id}_${ayah.numberInSurah}"
                    val customBgHex = profile.quranColoredAyahs[key]
                    val isBookmarked = profile.quranLastSurahId == surah.id &&
                        profile.quranLastAyahNumber == ayah.numberInSurah
                    val spanBg = when {
                        customBgHex != null -> Color(customBgHex).copy(alpha = 0.42f)
                        isBookmarked -> paperTheme.ornamentColor.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    }

                    pushStringAnnotation(tag = "AYAH", annotation = "${surah.id}:${ayah.numberInSurah}")
                    withStyle(SpanStyle(background = spanBg)) {
                        append(ayah.textUthmani)
                        withStyle(
                            SpanStyle(
                                color = paperTheme.ornamentColor,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" ﴿${OfflineQuranData.toArabicIndicDigits(ayah.numberInSurah)}﴾ ")
                        }
                    }
                    pop()
                }
            }
        }

        var layoutResult by remember(annotatedPageText) { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = annotatedPageText,
            style = TextStyle(
                fontFamily = AmiriFontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.72f).sp,
                color = paperTheme.textColor,
                textAlign = TextAlign.Justify
            ),
            onTextLayout = { layoutResult = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(segment) {
                    detectTapGestures { pos ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(pos)
                        val ann = annotatedPageText.getStringAnnotations("AYAH", offset, offset).firstOrNull()
                        if (ann != null) {
                            val parts = ann.item.split(":")
                            val ayahNum = parts.getOrNull(1)?.toIntOrNull()
                            val tappedAyah = segment.ayahs.find { it.numberInSurah == ayahNum }
                            if (tappedAyah != null) {
                                onTapAyah(surah, tappedAyah)
                            }
                        }
                    }
                }
        )
    }
}

@Composable
private fun AyahTafsirAndActionsDialog(
    surah: QuranSurah,
    ayah: QuranAyah,
    profile: TeacherProfile,
    highlightColors: List<Pair<Long, String>>,
    onBookmarkHere: () -> Unit,
    onSetHighlightColor: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val ext = LocalExtendedColors.current
    val detail = remember(surah.id, ayah.numberInSurah) {
        OfflineQuranTafsirHelper.lookup(surah.id, ayah.numberInSurah, ayah.textUthmani)
    }
    val key = "${surah.id}_${ayah.numberInSurah}"
    val currentHighlight = profile.quranColoredAyahs[key]
    val isBookmarked = profile.quranLastSurahId == surah.id && profile.quranLastAyahNumber == ayah.numberInSurah

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${surah.nameArabic} • آية ${OfflineQuranData.toArabicIndicDigits(ayah.numberInSurah)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "التفسير الميسر ومعاني الكلمات",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${ayah.textUthmani} ﴿${OfflineQuranData.toArabicIndicDigits(ayah.numberInSurah)}﴾",
                        fontFamily = AmiriFontFamily,
                        fontSize = 21.sp,
                        lineHeight = 36.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // التفسير الميسر
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "التفسير الميسر:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = detail.tafsirMuyassar,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // معاني الكلمات
                if (detail.wordMeanings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "معاني الكلمات:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            detail.wordMeanings.forEach { (word, meaning) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$word:",
                                        fontFamily = AmiriFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = meaning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Highlight swatches
                Text("تظليل وتلوين الآية:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    highlightColors.forEach { (hex, label) ->
                        val isSel = currentHighlight == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex).copy(alpha = 0.92f))
                                .border(if (isSel) 2.5.dp else 1.dp, Color.White, CircleShape)
                                .clickable { onSetHighlightColor(if (isSel) null else hex) }
                                .testTag("tafsir_color_$label"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Icon(Icons.Filled.Check, contentDescription = label, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (currentHighlight != null) {
                        TextButton(onClick = { onSetHighlightColor(null) }) {
                            Text("مسح اللون")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString("${ayah.textUthmani} (${surah.nameArabic}: ${ayah.numberInSurah})\n${detail.tafsirMuyassar}")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نسخ")
                    }
                    Button(
                        onClick = onBookmarkHere,
                        modifier = Modifier.weight(1f).testTag("tafsir_bookmark_btn")
                    ) {
                        Icon(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isBookmarked) "موضع التوقف ✓" else "حفظ التوقف")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
