package com.personal.weather.ui.charts

import com.personal.weather.forecast.AmountBlock
import com.personal.weather.forecast.Likelihood
import com.personal.weather.forecast.PrecipType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Pure geometry and label text for the charts, kept out of Canvas code so it can be tested. */
object ChartMath {
    data class YRange(val min: Double, val max: Double) {
        val span: Double get() = max - min
    }

    val PERCENT_RANGE = YRange(0.0, 100.0)
    private val DEFAULT_TEMP_RANGE = YRange(30.0, 80.0)

    /** Floor/ceil to the nearest 10°, at least 20° tall. */
    fun temperatureRange(values: List<Double?>): YRange {
        val present = values.filterNotNull()
        if (present.isEmpty()) return DEFAULT_TEMP_RANGE
        val lo = floor(present.min() / 10.0) * 10.0
        var hi = ceil(present.max() / 10.0) * 10.0
        if (hi - lo < 20.0) hi = lo + 20.0
        return YRange(lo, hi)
    }

    /** A y-range plus the spacing of its gridlines. */
    data class Axis(val range: YRange, val step: Double)

    /** Temperature axis: °F in 10s (min span 20); °C in 5s (min span 10, 0–25 with no data). */
    fun temperatureAxis(values: List<Double?>, celsius: Boolean): Axis {
        if (!celsius) return Axis(temperatureRange(values), 10.0)
        val present = values.filterNotNull()
        if (present.isEmpty()) return Axis(YRange(0.0, 25.0), 5.0)
        val lo = floor(present.min() / 5.0) * 5.0
        var hi = ceil(present.max() / 5.0) * 5.0
        if (hi - lo < 10.0) hi = lo + 10.0
        return Axis(YRange(lo, hi), 5.0)
    }

    /** Wind/gust axis in mph: from 0, gridlines every 5 (up to 20 mph) or 10, at least two steps tall. */
    fun windAxis(values: List<Double?>): Axis {
        val max = values.filterNotNull().maxOrNull() ?: 0.0
        val step = if (max <= 20.0) 5.0 else 10.0
        return Axis(YRange(0.0, maxOf(2 * step, ceil(max / step) * step)), step)
    }

    fun gridLines(range: YRange, step: Double): List<Double> =
        generateSequence(range.min) { it + step }.takeWhile { it <= range.max + 1e-9 }.toList()

    /** 0 at the day's local midnight, 1 at the next. Uses real elapsed time, so DST days stay correct. */
    fun xFraction(t: Instant, dayStart: Instant, dayEnd: Instant): Float =
        (Duration.between(dayStart, t).toMillis().toDouble() / Duration.between(dayStart, dayEnd).toMillis()).toFloat()

    /** 0 at the bottom of the range, 1 at the top, clamped. */
    fun yFraction(v: Double, range: YRange): Float = ((v - range.min) / range.span).toFloat().coerceIn(0f, 1f)

    /** Local hours 12a, 3a, 6a … 9p get x-axis labels and value labels. */
    fun isLabeledHour(t: Instant, zone: ZoneId): Boolean =
        ZonedDateTime.ofInstant(t, zone).let { it.hour % 3 == 0 && it.minute == 0 }

    fun hourLabel(t: Instant, zone: ZoneId): String {
        val hour = ZonedDateTime.ofInstant(t, zone).hour
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12${if (hour < 12) "a" else "p"}"
    }

    /** Center of the part of [block] that falls within the day. */
    fun amountLabelX(block: AmountBlock, dayStart: Instant, dayEnd: Instant): Float {
        val start = maxOf(block.start, dayStart)
        val end = minOf(block.end, dayEnd)
        return xFraction(start.plus(Duration.between(start, end).dividedBy(2)), dayStart, dayEnd)
    }

    fun amountText(block: AmountBlock): String = when (block.type) {
        PrecipType.RAIN -> inches(block.inches, 2)
        PrecipType.SNOW -> inches(block.inches, 1) + " snow"
        PrecipType.FREEZING_RAIN -> inches(block.inches, 2) + " ice"
        PrecipType.SLEET -> inches(block.inches, 2) + " sleet"
    }

    /** Ocnl fills the plot; each lower level is one quarter shorter. */
    fun barHeightFraction(level: Likelihood): Float = (level.ordinal + 1) / Likelihood.entries.size.toFloat()

    private fun inches(value: Double, decimals: Int): String {
        val smallest = 10.0.pow(-decimals)
        return if (value < smallest) "<${format(smallest, decimals)}\"" else "${format(value, decimals)}\""
    }

    private fun format(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
}
