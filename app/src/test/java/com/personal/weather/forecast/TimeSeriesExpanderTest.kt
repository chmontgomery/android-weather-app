package com.personal.weather.forecast

import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.NumericValue
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesExpanderTest {
    private val t0 = Instant.parse("2026-10-03T12:00:00Z")

    @Test fun parseValidTime_hours() {
        val (start, duration) = TimeSeriesExpander.parseValidTime("2026-10-03T12:00:00+00:00/PT2H")
        assertEquals(t0, start)
        assertEquals(Duration.ofHours(2), duration)
    }

    @Test fun parseValidTime_daysAndHours() {
        val (_, duration) = TimeSeriesExpander.parseValidTime("2026-10-03T12:00:00+00:00/P1DT11H")
        assertEquals(Duration.ofHours(35), duration)
    }

    @Test fun expandHourly_oneEntryPerCoveredHour() {
        val out = TimeSeriesExpander.expandHourly(listOf(
            "2026-10-03T12:00:00+00:00/PT2H" to "a",
            "2026-10-03T14:00:00+00:00/PT6H" to "b",
        ))
        assertEquals(8, out.size)
        assertEquals("a", out[t0])
        assertEquals("a", out[t0.plusSeconds(3600)])
        assertEquals("b", out[t0.plusSeconds(2 * 3600)])
        assertEquals("b", out[t0.plusSeconds(7 * 3600)])
    }

    @Test fun expandHourly_longDuration() {
        val out = TimeSeriesExpander.expandHourly(listOf("2026-10-03T12:00:00+00:00/P1DT11H" to 1))
        assertEquals(35, out.size)
    }

    @Test fun numeric_keepsNullsAsGaps() {
        val series = NumericSeries("wmoUnit:degC", listOf(NumericValue("2026-10-03T12:00:00+00:00/PT1H", null)))
        val out = TimeSeriesExpander.numeric(series)
        assertTrue(out.containsKey(t0))
        assertNull(out[t0])
    }

    @Test fun numeric_appliesConversion() {
        val series = NumericSeries("wmoUnit:degC", listOf(NumericValue("2026-10-03T12:00:00+00:00/PT1H", 100.0)))
        assertEquals(212.0, TimeSeriesExpander.numeric(series, Units::cToF)[t0]!!, 1e-9)
    }

    @Test fun speedUnits() {
        assertEquals(62.137119, Units.toMph("wmoUnit:km_h-1")(100.0), 1e-5)
        assertEquals(22.369363, Units.toMph("wmoUnit:m_s-1")(10.0), 1e-5)
        assertEquals(11.507794, Units.toMph("wmoUnit:kn")(10.0), 1e-5)
        assertEquals(62.137119, Units.toMph(null)(100.0), 1e-5)
    }

    @Test fun units() {
        assertEquals(32.0, Units.cToF(0.0), 1e-9)
        assertEquals(212.0, Units.cToF(100.0), 1e-9)
        assertEquals(1.0, Units.mmToInches(25.4), 1e-9)
        assertEquals(50.0, Units.toFahrenheit("wmoUnit:degF")(50.0), 1e-9)
        assertEquals(50.0, Units.toFahrenheit("wmoUnit:degC")(10.0), 1e-9)
        assertEquals(50.0, Units.toFahrenheit(null)(10.0), 1e-9)
    }
}
