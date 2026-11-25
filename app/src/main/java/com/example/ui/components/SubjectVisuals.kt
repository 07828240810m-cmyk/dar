package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.SubjectSpecialty
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SubjectVisualSpec(
    val primaryIcon: ImageVector,
    val badgeIcons: List<ImageVector>,
    val motifLabelAr: String
)

fun SubjectSpecialty.visualSpec(): SubjectVisualSpec = when (this) {
    SubjectSpecialty.BIOLOGY -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Biotech,
        badgeIcons = listOf(Icons.Filled.Biotech, Icons.Filled.Eco, Icons.Filled.Spa),
        motifLabelAr = "رموز علوم الحياة والأحياء"
    )
    SubjectSpecialty.PHYSICS -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Science,
        badgeIcons = listOf(Icons.Filled.Bolt, Icons.Filled.Science, Icons.Filled.Speed),
        motifLabelAr = "رموز الفيزياء والطاقة"
    )
    SubjectSpecialty.MATH -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Calculate,
        badgeIcons = listOf(Icons.Filled.Calculate, Icons.Filled.Functions, Icons.Filled.Timeline),
        motifLabelAr = "رموز الرياضيات والهندسة"
    )
    SubjectSpecialty.CHEMISTRY -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Science,
        badgeIcons = listOf(Icons.Filled.Science, Icons.Filled.BubbleChart, Icons.Filled.Biotech),
        motifLabelAr = "رموز الكيمياء والتفاعلات"
    )
    SubjectSpecialty.ARABIC -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.MenuBook,
        badgeIcons = listOf(Icons.Filled.MenuBook, Icons.Filled.HistoryEdu, Icons.Filled.AutoStories),
        motifLabelAr = "رموز اللغة العربية والأدب"
    )
    SubjectSpecialty.ENGLISH -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Translate,
        badgeIcons = listOf(Icons.Filled.Translate, Icons.Filled.Language, Icons.Filled.MenuBook),
        motifLabelAr = "رموز اللغة الإنكليزية"
    )
    SubjectSpecialty.SOCIAL_STUDIES -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Public,
        badgeIcons = listOf(Icons.Filled.Public, Icons.Filled.Explore, Icons.Filled.AccountBalance),
        motifLabelAr = "رموز الاجتماعيات والجغرافية"
    )
    SubjectSpecialty.ISLAMIC -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.AutoAwesome,
        badgeIcons = listOf(Icons.Filled.AutoAwesome, Icons.Filled.MenuBook, Icons.Filled.Mosque),
        motifLabelAr = "رموز التربية الإسلامية"
    )
    SubjectSpecialty.COMPUTER -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.Computer,
        badgeIcons = listOf(Icons.Filled.Computer, Icons.Filled.Code, Icons.Filled.Memory),
        motifLabelAr = "رموز الحاسوب والتقنية"
    )
    SubjectSpecialty.SPORTS -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.SportsSoccer,
        badgeIcons = listOf(Icons.Filled.SportsSoccer, Icons.Filled.EmojiEvents, Icons.Filled.FitnessCenter),
        motifLabelAr = "رموز التربية الرياضية"
    )
    SubjectSpecialty.GENERAL -> SubjectVisualSpec(
        primaryIcon = Icons.Filled.School,
        badgeIcons = listOf(Icons.Filled.School, Icons.Filled.AutoStories, Icons.Filled.Biotech),
        motifLabelAr = "الرموز التعليمية"
    )
}

@Composable
fun SubjectMotifCanvas(
    specialty: SubjectSpecialty,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.38f

        when (specialty) {
            SubjectSpecialty.BIOLOGY -> {
                // DNA Double Helix + Cellular Ring + Leaf silhouette motif
                val strandA = Path()
                val strandB = Path()
                val steps = 36
                val amplitude = w * 0.24f
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val y = h * 0.1f + t * h * 0.8f
                    val phase = t * 2.2f * PI.toFloat()
                    val xA = cx + sin(phase) * amplitude
                    val xB = cx - sin(phase) * amplitude
                    if (i == 0) {
                        strandA.moveTo(xA, y)
                        strandB.moveTo(xB, y)
                    } else {
                        strandA.lineTo(xA, y)
                        strandB.lineTo(xB, y)
                    }
                    if (i % 5 == 0 && i in 3 until steps - 2) {
                        drawLine(
                            color = secondaryColor.copy(alpha = 0.24f),
                            start = Offset(xA, y),
                            end = Offset(xB, y),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                drawPath(strandA, color = primaryColor.copy(alpha = 0.34f), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                drawPath(strandB, color = secondaryColor.copy(alpha = 0.30f), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                // Stylized Cell nucleus center
                drawCircle(
                    color = primaryColor.copy(alpha = 0.16f),
                    radius = r * 0.52f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.22f),
                    radius = r * 0.20f,
                    center = Offset(cx, cy)
                )
            }

            SubjectSpecialty.PHYSICS -> {
                // Atom orbits + central nucleus
                drawCircle(color = primaryColor.copy(alpha = 0.35f), radius = r * 0.16f, center = Offset(cx, cy))
                listOf(-32f, 32f, 90f).forEach { deg ->
                    rotate(degrees = deg, pivot = Offset(cx, cy)) {
                        drawOval(
                            color = primaryColor.copy(alpha = 0.24f),
                            topLeft = Offset(cx - r * 1.05f, cy - r * 0.38f),
                            size = Size(r * 2.1f, r * 0.76f),
                            style = Stroke(width = 3.2f)
                        )
                    }
                }
            }

            SubjectSpecialty.MATH -> {
                // Axes + Parabolic curve + Geometric triangle
                drawLine(primaryColor.copy(alpha = 0.22f), Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 2.5f)
                drawLine(primaryColor.copy(alpha = 0.22f), Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 2.5f)
                val curve = Path()
                for (i in 0..24) {
                    val nx = -1f + 2f * i / 24f
                    val px = cx + nx * r * 0.9f
                    val py = cy + (0.4f - nx * nx * 0.9f) * r
                    if (i == 0) curve.moveTo(px, py) else curve.lineTo(px, py)
                }
                drawPath(curve, color = primaryColor.copy(alpha = 0.34f), style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawCircle(secondaryColor.copy(alpha = 0.20f), radius = r * 0.85f, center = Offset(cx, cy), style = Stroke(2.5f))
            }

            SubjectSpecialty.CHEMISTRY -> {
                // Hexagon benzene ring + electron nodes
                val hex = Path()
                for (i in 0..5) {
                    val ang = (i * 60f - 30f) * PI.toFloat() / 180f
                    val px = cx + cos(ang) * r * 0.78f
                    val py = cy + sin(ang) * r * 0.78f
                    if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
                    drawCircle(secondaryColor.copy(alpha = 0.30f), radius = 5f, center = Offset(px, py))
                }
                hex.close()
                drawPath(hex, color = primaryColor.copy(alpha = 0.32f), style = Stroke(width = 4f))
                drawCircle(primaryColor.copy(alpha = 0.22f), radius = r * 0.44f, center = Offset(cx, cy), style = Stroke(3f))
            }

            SubjectSpecialty.SOCIAL_STUDIES -> {
                // Meridian globe wireframe
                drawCircle(primaryColor.copy(alpha = 0.28f), radius = r * 0.9f, center = Offset(cx, cy), style = Stroke(3.5f))
                drawOval(
                    color = secondaryColor.copy(alpha = 0.24f),
                    topLeft = Offset(cx - r * 0.45f, cy - r * 0.9f),
                    size = Size(r * 0.9f, r * 1.8f),
                    style = Stroke(2.8f)
                )
                drawLine(primaryColor.copy(alpha = 0.24f), Offset(cx - r * 0.9f, cy), Offset(cx + r * 0.9f, cy), strokeWidth = 2.8f)
            }

            else -> {
                // Layered Star / Knowledge Medallion Motif
                drawCircle(primaryColor.copy(alpha = 0.16f), radius = r * 0.95f, center = Offset(cx, cy), style = Stroke(3f))
                rotate(45f, pivot = Offset(cx, cy)) {
                    drawRect(
                        color = secondaryColor.copy(alpha = 0.22f),
                        topLeft = Offset(cx - r * 0.58f, cy - r * 0.58f),
                        size = Size(r * 1.16f, r * 1.16f),
                        style = Stroke(3f)
                    )
                }
                drawCircle(primaryColor.copy(alpha = 0.26f), radius = r * 0.28f, center = Offset(cx, cy))
            }
        }
    }
}
