package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.GradeLevel
import com.example.model.SectionLetter
import com.example.ui.theme.LocalExtendedColors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.neonCardGlow(
    glowColor: Color,
    enabled: Boolean,
    cornerRadius: Dp = 18.dp,
    alphaFactor: Float = 0.28f
): Modifier = if (!enabled) this else this.drawBehind {
    val rad = cornerRadius.toPx()
    drawRoundRect(
        color = glowColor.copy(alpha = alphaFactor * 0.45f),
        cornerRadius = CornerRadius(rad + 3.dp.toPx(), rad + 3.dp.toPx()),
        style = Stroke(width = 4.5.dp.toPx())
    )
    drawRoundRect(
        color = glowColor.copy(alpha = alphaFactor),
        cornerRadius = CornerRadius(rad, rad),
        style = Stroke(width = 1.6.dp.toPx())
    )
}

class NeonTapController(
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    val glowAnim = Animatable(0f)
    val scaleAnim = Animatable(1f)

    fun trigger(onCompleteAction: (() -> Unit)? = null) {
        scope.launch {
            glowAnim.snapTo(1f)
            scaleAnim.snapTo(1.055f)
            launch {
                scaleAnim.animateTo(
                    targetValue = 0.965f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing)
                )
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
            }
            launch {
                glowAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            }
            if (onCompleteAction != null) {
                delay(175L)
                onCompleteAction()
            }
        }
    }
}

@Composable
fun rememberNeonTapController(): NeonTapController {
    val scope = rememberCoroutineScope()
    return remember(scope) { NeonTapController(scope) }
}

fun Modifier.neonTapFeedback(
    controller: NeonTapController,
    glowColor: Color,
    cornerRadius: Dp = 18.dp
): Modifier {
    val flash = controller.glowAnim.value
    val sc = controller.scaleAnim.value
    return this
        .scale(sc)
        .drawBehind {
            if (flash > 0.01f) {
                val rad = cornerRadius.toPx()
                drawRoundRect(
                    color = glowColor.copy(alpha = flash * 0.30f),
                    cornerRadius = CornerRadius(rad, rad)
                )
                drawRoundRect(
                    color = glowColor.copy(alpha = flash * 0.65f),
                    cornerRadius = CornerRadius(rad + 5.dp.toPx(), rad + 5.dp.toPx()),
                    style = Stroke(width = 7.dp.toPx())
                )
                drawRoundRect(
                    color = glowColor.copy(alpha = flash * 0.95f),
                    cornerRadius = CornerRadius(rad, rad),
                    style = Stroke(width = 2.6.dp.toPx())
                )
            }
        }
}

@Composable
fun FeatureInfoIcon(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("info_btn_$title")
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "شرح $title",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("فهمت")
                }
            }
        )
    }
}

@Composable
fun SectionHeaderWithInfo(
    title: String,
    infoTitle: String,
    infoDescription: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FeatureInfoIcon(title = infoTitle, description = infoDescription)
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun ClassSelectorBar(
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    onSelect: (GradeLevel, SectionLetter) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradeLevel.entries.forEach { grade ->
                val active = grade == selectedGrade
                FilterChip(
                    selected = active,
                    onClick = { onSelect(grade, selectedSection) },
                    label = {
                        Text(
                            text = grade.titleAr,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = ext.cardElevated,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("grade_chip_${grade.id}")
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLetter.entries.forEach { sec ->
                val active = sec == selectedSection
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.secondary else ext.cardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.secondary else ext.borderSubtle
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(selectedGrade, sec) }
                        .testTag("section_chip_${sec.id}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sec.titleAr,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBadgeCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    Surface(
        modifier = modifier.neonCardGlow(accentColor, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.34f),
        shape = RoundedCornerShape(18.dp),
        color = ext.cardElevated,
        border = BorderStroke(
            width = if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
            color = accentColor.copy(alpha = if (ext.neonGlowEnabled) 0.55f else 0.30f)
        ),
        shadowElevation = if (ext.neonGlowEnabled) 6.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(ext.cardElevated, ext.cardSecondary)
                    )
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(23.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GradientBannerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val ext = LocalExtendedColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .neonCardGlow(primary, ext.neonGlowEnabled, 22.dp, alphaFactor = 0.32f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(if (ext.neonGlowEnabled) 1.4.dp else 1.dp, primary.copy(alpha = if (ext.neonGlowEnabled) 0.55f else 0.30f)),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(ext.cardElevated, ext.cardSecondary)
                    )
                )
                .padding(18.dp),
            content = content
        )
    }
}
