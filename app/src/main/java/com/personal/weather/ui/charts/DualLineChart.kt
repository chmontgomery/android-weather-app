package com.personal.weather.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.weather.forecast.DayForecast
import com.personal.weather.ui.charts.LabelPlacer.Box
import com.personal.weather.ui.charts.LabelPlacer.Pt
import com.personal.weather.ui.theme.LocalChartColors
import java.time.ZoneId

/**
 * One line on a chart; [values] is index-aligned with DayForecast.hours, null = gap.
 * [hideLabelAt] suppresses this line's value label at an hour index (e.g. when another line's label says the same).
 */
data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Double?>,
    val hideLabelAt: (Int) -> Boolean = { false },
)

/** Line chart for one day. Value labels every 3 hours are placed so they never cover a line or each other. */
@Composable
fun DualLineChart(
    day: DayForecast,
    zone: ZoneId,
    series: List<LineSeries>,
    range: ChartMath.YRange,
    gridStep: Double,
    axisLabel: (Double) -> String,
    /** Text for a value label; null means no label for that value. */
    valueLabel: (Double) -> String?,
    nowFraction: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    Column(modifier) {
        ChartLegend(series.map { it.label to it.color })
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val plot = plotRect()
            for (v in ChartMath.gridLines(range, gridStep)) {
                drawYGridLine(plot.yAt(ChartMath.yFraction(v, range)), axisLabel(v), plot, colors, measurer)
            }
            drawTimeAxis(day, zone, plot, colors, measurer)

            val drawn = series.map { PlottedSeries(it, plotPoints(day, it, range, plot)) }
            drawn.forEach { drawLines(it) }
            drawValueLabels(drawn, day, zone, plot, measurer, nowFraction, valueLabel)
            drawNowLine(nowFraction, plot, colors)
        }
    }
}

/** A series' points on screen: one inner list per run of consecutive non-null values; [byHour] keeps the hour index. */
private class PlottedSeries(val series: LineSeries, val byHour: Map<Int, Offset>) {
    val runs: List<List<Offset>> = buildList {
        var run = mutableListOf<Offset>()
        for (i in byHour.keys.sorted()) {
            if (run.isNotEmpty() && (i - 1) !in byHour) {
                add(run)
                run = mutableListOf()
            }
            run += byHour.getValue(i)
        }
        if (run.isNotEmpty()) add(run)
    }
}

private fun plotPoints(day: DayForecast, series: LineSeries, range: ChartMath.YRange, plot: Rect): Map<Int, Offset> =
    day.hours.mapIndexedNotNull { i, hour ->
        series.values.getOrNull(i)?.let { v ->
            i to Offset(plot.xAt(ChartMath.xFraction(hour.start, day.start, day.end)), plot.yAt(ChartMath.yFraction(v, range)))
        }
    }.toMap()

private fun DrawScope.drawLines(p: PlottedSeries) {
    p.runs.forEach { run ->
        val path = Path().apply {
            moveTo(run.first().x, run.first().y)
            run.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, p.series.color, style = Stroke(width = LINE_WIDTH.toPx()))
        run.forEach { drawCircle(p.series.color, radius = DOT_RADIUS.toPx(), center = it) }
    }
}

/** Labels every 3 hours, earlier series first, each placed clear of every line (incl. "now"), dot and earlier label. */
private fun DrawScope.drawValueLabels(
    drawn: List<PlottedSeries>,
    day: DayForecast,
    zone: ZoneId,
    plot: Rect,
    measurer: TextMeasurer,
    nowFraction: Float?,
    format: (Double) -> String?,
) {
    val nowLine = nowFraction?.let { f -> plot.xAt(f).let { x -> listOf(Pt(x, plot.top), Pt(x, plot.bottom)) } }
    val layouts = mutableListOf<TextLayoutResult>()
    val requests = mutableListOf<LabelPlacer.Request>()
    drawn.forEach { p ->
        val style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.series.color)
        p.byHour.forEach { (i, point) ->
            val value = p.series.values[i] ?: return@forEach
            if (!ChartMath.isLabeledHour(day.hours[i].start, zone) || p.series.hideLabelAt(i)) return@forEach
            val text = format(value) ?: return@forEach
            val layout = measurer.measure(text, style)
            layouts += layout
            requests += LabelPlacer.Request(Pt(point.x, point.y), layout.size.width.toFloat(), layout.size.height.toFloat())
        }
    }
    val placements = LabelPlacer.place(
        requests = requests,
        lines = drawn.flatMap { p -> p.runs.map { run -> run.map { Pt(it.x, it.y) } } } + listOfNotNull(nowLine),
        dots = drawn.flatMap { p -> p.byHour.values.map { Pt(it.x, it.y) } },
        dotRadius = DOT_RADIUS.toPx(),
        gap = 2.dp.toPx(),
        bounds = Box(plot.left + VALUE_LABEL_INSET.toPx(), 0f, plot.right, plot.bottom),
        pad = LINE_WIDTH.toPx() / 2 + 0.5.dp.toPx(),
    )
    placements.forEachIndexed { k, topLeft ->
        if (topLeft != null) drawText(layouts[k], topLeft = Offset(topLeft.x, topLeft.y))
    }
}

private val LINE_WIDTH = 2.dp
private val DOT_RADIUS = 2.dp
