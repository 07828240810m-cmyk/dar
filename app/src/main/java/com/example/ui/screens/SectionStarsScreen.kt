package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.LocalExtendedColors
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private val starGlowPalette = listOf(0xFFFFD54FL, 0xFF40D6B4L, 0xFF54A0FFL, 0xFFFF6B81L, 0xFFA55EEAL)
private val starEmblems = listOf("⭐", "🌟", "👑", "🧬", "🏆", "💎", "🚀", "🎓")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionStarsScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    getStarPhoto: (String?) -> File?,
    onSelectClass: (GradeLevel, SectionLetter) -> Unit,
    onSaveStar: (SectionStarEntry, Uri?) -> Unit,
    onDeleteStar: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val ext = LocalExtendedColors.current
    val primary = MaterialTheme.colorScheme.primary
    var showAddDialog by remember { mutableStateOf(false) }

    val sectionStars = remember(state.sectionStars, selectedGrade, selectedSection) {
        state.sectionStars.filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
    }
    val sectionStudents = remember(state.students, selectedGrade, selectedSection) {
        state.students.filter { it.gradeLevel == selectedGrade && it.section == selectedSection && !it.isTransferred }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("section_stars_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("كواكب الشعبة", fontWeight = FontWeight.Bold)
                    Text(
                        "خريطة المتميزين ونجوم ${selectedGrade.shortAr} (${selectedSection.id})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stars_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            },
            actions = {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(end = 8.dp).testTag("add_section_star_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة كوكب")
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GradeLevel.entries) { g ->
                FilterChip(
                    selected = selectedGrade == g,
                    onClick = { onSelectClass(g, selectedSection) },
                    label = { Text(g.shortAr) }
                )
            }
            items(SectionLetter.entries) { s ->
                FilterChip(
                    selected = selectedSection == s,
                    onClick = { onSelectClass(selectedGrade, s) },
                    label = { Text("شعبة ${s.id}") }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GalaxyOrbitMapCard(
                    grade = selectedGrade,
                    section = selectedSection,
                    stars = sectionStars,
                    getStarPhoto = getStarPhoto
                )
            }

            if (sectionStars.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("لم يتم تعيين كواكب لهذه الشعبة بعد", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "أضف الطلبة المتفوقين بصورهم أو برموزهم الخاصة ليظهروا في مدارات مجرة الشعبة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { showAddDialog = true }) {
                                Text("تعيين أول نجم للشعبة")
                            }
                        }
                    }
                }
            } else {
                items(sectionStars, key = { it.id }) { star ->
                    val glow = Color(star.glowColorHex)
                    val photoFile = getStarPhoto(star.photoFileName)
                    val bmp = remember(photoFile?.absolutePath) {
                        photoFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, glow.copy(alpha = 0.65f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.neonCardGlow(glow, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = glow.copy(alpha = 0.2f),
                                border = BorderStroke(2.dp, glow),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = star.studentName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(star.emblem, fontSize = 24.sp)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(star.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${star.customTitle} • المدار ${star.orbitTier}", style = MaterialTheme.typography.bodySmall, color = glow)
                            }
                            IconButton(onClick = { onDeleteStar(star.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSectionStarDialog(
            grade = selectedGrade,
            section = selectedSection,
            students = sectionStudents,
            onDismiss = { showAddDialog = false },
            onSave = { entry, uri ->
                onSaveStar(entry, uri)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun GalaxyOrbitMapCard(
    grade: GradeLevel,
    section: SectionLetter,
    stars: List<SectionStarEntry>,
    getStarPhoto: (String?) -> File?
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF172447), Color(0xFF0A1024), Color(0xFF040712))
                )
            )
            .border(1.5.dp, Color(0x66FFD54F), RoundedCornerShape(26.dp))
    ) {
        val boxW = maxWidth
        val boxH = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r1 = size.minDimension * 0.23f
            val r2 = size.minDimension * 0.37f
            val r3 = size.minDimension * 0.46f

            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawCircle(Color(0x44FFD54F), radius = r1, center = Offset(cx, cy), style = Stroke(2f, pathEffect = dash))
            drawCircle(Color(0x4440D6B4), radius = r2, center = Offset(cx, cy), style = Stroke(2f, pathEffect = dash))
            drawCircle(Color(0x3354A0FF), radius = r3, center = Offset(cx, cy), style = Stroke(1.5f, pathEffect = dash))

            // Background constellation dots
            val bgDots = listOf(
                0.12f to 0.15f, 0.85f to 0.18f, 0.22f to 0.82f, 0.88f to 0.78f,
                0.52f to 0.10f, 0.14f to 0.50f, 0.84f to 0.45f, 0.65f to 0.88f
            )
            bgDots.forEach { (fx, fy) ->
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 2.5f, center = Offset(size.width * fx, size.height * fy))
            }

            // Lines connecting center star to student planets
            stars.forEachIndexed { idx, star ->
                val radius = when (star.orbitTier) {
                    1 -> r1
                    2 -> r2
                    else -> r3
                }
                val angleDeg = (idx * (360f / stars.size.coerceAtLeast(1))) - 90f
                val rad = Math.toRadians(angleDeg.toDouble())
                val px = cx + (radius * cos(rad)).toFloat()
                val py = cy + (radius * sin(rad)).toFloat()
                drawLine(
                    color = Color(star.glowColorHex).copy(alpha = 0.55f),
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 2.2f
                )
            }
        }

        // Central sun badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF1F2F60),
            border = BorderStroke(2.5.dp, Color(0xFFFFD54F)),
            modifier = Modifier
                .align(Alignment.Center)
                .size(68.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text("✨", fontSize = 18.sp)
                Text(
                    "${grade.shortAr}\n(${section.id})",
                    color = Color(0xFFFFD54F),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Student planet nodes positioned along orbits
        stars.forEachIndexed { idx, star ->
            val radiusFraction = when (star.orbitTier) {
                1 -> 0.23f
                2 -> 0.37f
                else -> 0.44f
            }
            val minDim = minOf(boxW, boxH)
            val rDp = minDim * radiusFraction
            val angleDeg = (idx * (360f / stars.size.coerceAtLeast(1))) - 90f
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = (rDp.value * cos(rad)).dp
            val dy = (rDp.value * sin(rad)).dp
            val glow = Color(star.glowColorHex)
            val photo = getStarPhoto(star.photoFileName)
            val bmp = remember(photo?.absolutePath) {
                photo?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dx, y = dy)
                    .width(74.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF111B38),
                    border = BorderStroke(2.dp, glow),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = star.studentName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(star.emblem, fontSize = 18.sp)
                        }
                    }
                }
                Text(
                    text = star.studentName.split(" ").take(2).joinToString(" "),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AddSectionStarDialog(
    grade: GradeLevel,
    section: SectionLetter,
    students: List<Student>,
    onDismiss: () -> Unit,
    onSave: (SectionStarEntry, Uri?) -> Unit
) {
    var selectedStudentId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("نجم الشعبة") }
    var emblem by remember { mutableStateOf("⭐") }
    var glowHex by remember { mutableStateOf(starGlowPalette.first()) }
    var orbitTier by remember { mutableStateOf(1) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إضافة طالب لكواكب الشعبة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (students.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(students.take(30)) { st ->
                            AssistChip(
                                onClick = {
                                    selectedStudentId = st.id
                                    name = st.fullName
                                },
                                label = { Text(st.fullName, maxLines = 1) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطالب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("اللقب (مثال: الأول في الأحياء)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(starEmblems) { sym ->
                        FilterChip(selected = emblem == sym, onClick = { emblem = sym }, label = { Text(sym, fontSize = 18.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("التوهج:", style = MaterialTheme.typography.labelMedium)
                    starGlowPalette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(if (glowHex == hex) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { glowHex = hex }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "المدار 1", 2 to "المدار 2", 3 to "المدار 3").forEach { (tier, lbl) ->
                        FilterChip(selected = orbitTier == tier, onClick = { orbitTier = tier }, label = { Text(lbl) })
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (photoUri == null) "اختيار صورة للطالب (اختياري)" else "✓ تم اختيار الصورة")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    SectionStarEntry(
                                        id = "star_${System.currentTimeMillis()}",
                                        studentId = selectedStudentId,
                                        studentName = name.trim(),
                                        gradeLevel = grade,
                                        section = section,
                                        customTitle = customTitle.trim().ifBlank { "نجم الشعبة" },
                                        emblem = emblem,
                                        glowColorHex = glowHex,
                                        orbitTier = orbitTier
                                    ),
                                    photoUri
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ الكوكب") }
                }
            }
        }
    }
}
