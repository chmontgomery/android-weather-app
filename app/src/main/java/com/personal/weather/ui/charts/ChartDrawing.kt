package com.personal.weather.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.weather.forecast.DayForecast
import com.personal.weather.ui.theme.ChartColors
import java.time.ZoneId

internal val AXIS_WIDTH = 34.dp

/** Space between a y-axis number and the plot edge, so a line's first dot (at the edge) never touches it. */
private val AXIS_LABEL_GAP = 5.dp

/** Value labels keep this far inside the plot's left edge so they don't run into the y-axis numbers. */
internal val VALUE_LABEL_INSET = 4.dp
private val RIGHT_INSET = 6.dp
private val TOP_INSET = 14.dp
private val BOTTOM_INSET = 16.dp

internal fun axisTextStyle(colors: ChartColors) = TextStyle(fontSize = 10.sp, color = colors.axisText)

/** The plotting rectangle, leaving room for y labels on the left, value labels on top, and hour labels below. */
internal fun DrawScope.plotRect(): Rect = Rect(
    left = AXIS_WIDTH.toPx(),
    top = TOP_INSET.toPx(),
    right = maxOf(AXIS_WIDTH.toPx(), size.width - RIGHT_INSET.toPx()),
    bottom = maxOf(TOP_INSET.toPx(), size.height - BOTTOM_INSET.toPx()),
)

internal fun Rect.xAt(fraction: Float): Float = left + fraction * width
internal fun Rect.yAt(fraction: Float): Float = bottom - fraction * height

/** Keeps a label of width [w] inside [min, max] without throwing when it doesn't fit. */
internal fun clampLabelX(x: Float, w: Float, min: Float, max: Float): Float = x.coerceAtMost(max - w).coerceAtLeast(min)

/**
 * Horizontal gridline with its label in the left axis gutter. A label on the bottom gridline sits just above it
 * so it doesn't collide with the "12a" hour label below the corner.
 */
internal fun DrawScope.drawYGridLine(y: Float, label: String, plot: Rect, colors: ChartColors, measurer: TextMeasurer) {
    drawLine(colors.grid.copy(alpha = 0.6f), Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
    val layout = measurer.measure(label, axisTextStyle(colors))
    drawText(layout, topLeft = Offset(maxOf(0f, plot.left - layout.size.width - AXIS_LABEL_GAP.toPx()), axisLabelTop(y, layout.size.height, plot)))
}

private fun axisLabelTop(y: Float, height: Int, plot: Rect): Float =
    if (y >= plot.bottom - 0.5f) y - height else y - height / 2f

/** Hourly vertical gridlines (darker every 3 hours) and "12a 3a 6a …" labels under the plot. */
internal fun DrawScope.drawTimeAxis(day: DayForecast, zone: ZoneId, plot: Rect, colors: ChartColors, measurer: TextMeasurer) {
    for (hour in day.hours) {
        val x = plot.xAt(ChartMath.xFraction(hour.start, day.start, day.end))
        val major = ChartMath.isLabeledHour(hour.start, zone)
        drawLine(colors.grid.copy(alpha = if (major) 1f else 0.45f), Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
        if (major) {
            val layout = measurer.measure(ChartMath.hourLabel(hour.start, zone), axisTextStyle(colors))
            val left = clampLabelX(x - layout.size.width / 2f, layout.size.width.toFloat(), 0f, size.width)
            drawText(layout, topLeft = Offset(left, plot.bottom + 2f))
        }
    }
    drawLine(colors.grid, Offset(plot.right, plot.top), Offset(plot.right, plot.bottom), strokeWidth = 1f)
    drawLine(colors.grid, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1f)
}

/** Dashed vertical line at the current time (today's page only). */
internal fun DrawScope.drawNowLine(nowFraction: Float?, plot: Rect, colors: ChartColors) {
    if (nowFraction == null) return
    val x = plot.xAt(nowFraction)
    drawLine(
        colors.now, Offset(x, plot.top), Offset(x, plot.bottom),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(start = AXIS_WIDTH, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
