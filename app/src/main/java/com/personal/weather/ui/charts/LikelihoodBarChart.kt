package com.personal.weather.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.personal.weather.forecast.DayForecast
import com.personal.weather.forecast.Likelihood
import com.personal.weather.forecast.PrecipType
import com.personal.weather.ui.theme.LocalChartColors
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun precipLabel(type: PrecipType): String = when (type) {
    PrecipType.RAIN -> "Rain"
    PrecipType.SNOW -> "Snow"
    PrecipType.FREEZING_RAIN -> "Frz rain"
    PrecipType.SLEET -> "Sleet"
}

/** Hourly bars whose height is the NWS likelihood level, one color per precip type, with 6-hour amount labels. */
@Composable
fun LikelihoodBarChart(day: DayForecast, zone: ZoneId, nowFraction: Float?, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    val hasPrecip = day.hours.any { it.likelihood.isNotEmpty() }
    Column(modifier) {
        ChartLegend(PrecipType.entries.map { precipLabel(it) to colors.precip(it) })
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val plot = plotRect()
            Likelihood.entries.forEach { level ->
                drawYGridLine(plot.yAt(ChartMath.barHeightFraction(level)), level.axisLabel, plot, colors, measurer)
            }
            drawTimeAxis(day, zone, plot, colors, measurer)

            day.hours.forEach { hour ->
                val types = PrecipType.entries.filter { it in hour.likelihood }
                if (types.isEmpty()) return@forEach
                val x0 = plot.xAt(ChartMath.xFraction(hour.start, day.start, day.end))
                val x1 = plot.xAt(ChartMath.xFraction(hour.start.plus(1, ChronoUnit.HOURS), day.start, day.end).coerceAtMost(1f))
                val slot = x1 - x0
                val barWidth = slot * 0.8f / types.size
                types.forEachIndexed { i, type ->
                    val top = plot.yAt(ChartMath.barHeightFraction(hour.likelihood.getValue(type)))
                    drawRect(
                        colors.precip(type),
                        topLeft = Offset(x0 + slot * 0.1f + i * barWidth, top),
                        size = Size(barWidth, plot.bottom - top),
                    )
                }
            }

            day.amounts.groupBy { ChartMath.amountLabelX(it, day.start, day.end) }.forEach { (fx, blocks) ->
                blocks.forEachIndexed { row, block ->
                    val style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.precip(block.type))
                    val layout = measurer.measure(ChartMath.amountText(block), style)
                    val left = clampLabelX(plot.xAt(fx) - layout.size.width / 2f, layout.size.width.toFloat(), plot.left, plot.right)
                    drawText(layout, topLeft = Offset(left, maxOf(0f, plot.top - layout.size.height + row * layout.size.height)))
                }
            }

            if (!hasPrecip) {
                val layout = measurer.measure("No precipitation expected", axisTextStyle(colors).copy(color = colors.axisText.copy(alpha = 0.6f)))
                drawText(layout, topLeft = Offset(plot.center.x - layout.size.width / 2f, plot.center.y - layout.size.height / 2f))
            }
            drawNowLine(nowFraction, plot, colors)
        }
    }
}
