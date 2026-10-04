package com.personal.weather.forecast

import com.personal.weather.nws.NumericSeries
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** Turns NWS interval-valued series into one value per hour. */
object TimeSeriesExpander {
    /** Parses "2026-10-03T12:00:00+00:00/PT2H" into (start, duration). */
    fun parseValidTime(validTime: String): Pair<Instant, Duration> {
        val (start, duration) = validTime.split("/", limit = 2)
        return OffsetDateTime.parse(start).toInstant() to Duration.parse(duration)
    }

    /** One map entry per hour each interval covers. Later entries win if intervals overlap. */
    fun <T> expandHourly(entries: List<Pair<String, T>>): Map<Instant, T> {
        val out = LinkedHashMap<Instant, T>()
        for ((validTime, value) in entries) {
            val (start, duration) = parseValidTime(validTime)
            val first = start.truncatedTo(ChronoUnit.HOURS)
            val hours = maxOf(1L, duration.toHours())
            for (h in 0 until hours) out[first.plus(h, ChronoUnit.HOURS)] = value
        }
        return out
    }

    /** Null values stay as null entries so charts can draw gaps. */
    fun numeric(series: NumericSeries, convert: (Double) -> Double = { it }): Map<Instant, Double?> =
        expandHourly(series.values.map { it.validTime to it.value?.let(convert) })
}

object Units {
    fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0
    fun mmToInches(mm: Double): Double = mm / 25.4

    /** NWS gridpoint wind is km/h; handle m/s and knots too in case a series ever uses them. */
    fun toMph(uom: String?): (Double) -> Double = when {
        uom?.endsWith("m_s-1") == true -> { v -> v * 2.2369363 }
        uom?.endsWith("kn") == true -> { v -> v * 1.1507794 }
        else -> { v -> v / 1.609344 }
    }

    /** NWS gridpoints report degC; pass through if a series is ever already in degF. */
    fun toFahrenheit(uom: String?): (Double) -> Double =
        if (uom?.endsWith("degF") == true) { v -> v } else ::cToF
}
