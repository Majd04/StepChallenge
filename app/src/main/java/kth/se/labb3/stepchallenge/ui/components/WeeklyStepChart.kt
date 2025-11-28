package kth.se.labb3.stepchallenge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kth.se.labb3.stepchallenge.data.model.DailyStepSummary
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeeklyStepChart(
    data: List<DailyStepSummary>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val maxSteps = remember(data) {
        maxOf(data.maxOfOrNull { it.steps } ?: 0L, data.firstOrNull()?.goal?.toLong() ?: 10000L)
    }

    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val density = LocalDensity.current

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val barWidth = size.width / (data.size * 2f)
            val spacing = barWidth
            val chartHeight = size.height - 40.dp.toPx()
            val goalY = chartHeight * (1 - (data.firstOrNull()?.goal?.toFloat() ?: 10000f) / maxSteps.toFloat())

            // Draw goal line
            drawLine(
                color = goalColor.copy(alpha = 0.5f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 2.dp.toPx()
            )

            // Draw bars
            data.forEachIndexed { index, summary ->
                val barHeight = if (maxSteps > 0) {
                    (summary.steps.toFloat() / maxSteps.toFloat()) * chartHeight
                } else 0f

                val x = spacing + (index * (barWidth + spacing))
                val y = chartHeight - barHeight

                // Bar background
                drawRoundRect(
                    color = surfaceVariant,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                // Actual bar
                val barColor = if (summary.steps >= summary.goal) {
                    primaryColor
                } else {
                    primaryColor.copy(alpha = 0.7f)
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }

            // Draw day labels
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = with(density) { 12.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
            }

            data.forEachIndexed { index, summary ->
                val x = spacing + (index * (barWidth + spacing)) + barWidth / 2
                val dayName = summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

                drawContext.canvas.nativeCanvas.drawText(
                    dayName,
                    x,
                    size.height - 10.dp.toPx(),
                    textPaint
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                drawLine(
                    color = goalColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx()
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Goal: ${numberFormat.format(data.firstOrNull()?.goal ?: 10000)}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
        }
    }
}