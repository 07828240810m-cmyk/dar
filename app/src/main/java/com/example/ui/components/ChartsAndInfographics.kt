package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

data class BarChartEntry(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun DonutPassRateChart(
    passRate: Float,
    passingCount: Int,
    failingCount: Int,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val passColor = SuccessGreen
    val failColor = DangerRed
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                val sweepPass = (passRate.coerceIn(0f, 100f) / 100f) * 360f
                if (sweepPass > 0f) {
                    drawArc(
                        color = passColor,
                        startAngle = -90f,
                        sweepAngle = sweepPass,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${passRate.toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "نسبة النجاح",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalExtendedColors.current.textMuted
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            LegendItem(color = passColor, label = "الناجحون", count = "$passingCount طالب")
            LegendItem(color = failColor, label = "دون حد النجاح", count = "$failingCount طالب")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = count,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VerticalBarComparisonChart(
    entries: List<BarChartEntry>,
    maxValue: Float = 100f,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (entries.isEmpty()) return@Canvas
            val lines = 4
            for (i in 0..lines) {
                val y = size.height * (i / lines.toFloat())
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val slotWidth = size.width / entries.size
            val barWidth = (slotWidth * 0.52f).coerceAtMost(42.dp.toPx())
            entries.forEachIndexed { idx, entry ->
                val ratio = (entry.value / maxValue).coerceIn(0f, 1f)
                val barHeight = size.height * ratio
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight
                drawRoundRect(
                    color = entry.color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            entries.forEach { entry ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${entry.value.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = entry.color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalExtendedColors.current.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TrendTrajectoryChart(
    improved: Int,
    stable: Int,
    declined: Int,
    modifier: Modifier = Modifier
) {
    val total = (improved + stable + declined).coerceAtLeast(1)
    val ext = LocalExtendedColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ext.cardElevated,
        border = BorderStroke(1.dp, ext.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "تحليل مسار المستوى (زاد / بقي / قل)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            // Stacked horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (improved > 0) {
                    Box(
                        modifier = Modifier
                            .weight(improved.toFloat() / total)
                            .fillMaxHeight()
                            .background(SuccessGreen)
                    )
                }
                if (stable > 0) {
                    Box(
                        modifier = Modifier
                            .weight(stable.toFloat() / total)
                            .fillMaxHeight()
                            .background(InfoBlue)
                    )
                }
                if (declined > 0) {
                    Box(
                        modifier = Modifier
                            .weight(declined.toFloat() / total)
                            .fillMaxHeight()
                            .background(DangerRed)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TrendPill("زاد مستواهم", improved, SuccessGreen)
                TrendPill("مستوى ثابت", stable, InfoBlue)
                TrendPill("قل مستواهم", declined, DangerRed)
            }
        }
    }
}

@Composable
private fun TrendPill(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$count", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ProgressionSparklineChart(
    points: List<Pair<String, Float>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(16.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            val path = Path()
            val coords = points.mapIndexed { idx, (_, value) ->
                val x = idx * stepX
                val y = size.height - (value.coerceIn(0f, 100f) / 100f) * size.height
                Offset(x, y)
            }
            coords.forEachIndexed { idx, pt ->
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            coords.forEach { pt ->
                drawCircle(color = lineColor, radius = 6.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = pt)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { (lbl, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${value.toInt()}", style = MaterialTheme.typography.labelLarge, color = lineColor, fontWeight = FontWeight.Bold)
                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.textMuted)
                }
            }
        }
    }
}
