package com.personal.weather.forecast

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected sunrise/sunset times are from the US Naval Observatory (aa.usno.navy.mil), in UTC. */
class SunTimesTest {
    private fun assertNear(expected: String, actual: Instant) {
        val off = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("expected ~$expected, got $actual", off <= Duration.ofMinutes(2))
    }

    /** Asserts the day has two nights: midnight → [sunrise], and [sunset] → midnight. */
    private fun assertTwoNights(start: Instant, end: Instant, nights: List<SunTimes.Night>, sunrise: String, sunset: String) {
        assertEquals(2, nights.size)
        assertEquals(start, nights[0].start)
        assertNear(sunrise, nights[0].end)
        assertNear(sunset, nights[1].start)
        assertEquals(end, nights[1].end)
    }

    @Test fun nights_chicagoInAutumn() {
        val start = Instant.parse("2026-10-31T05:00:00Z")
        val end = Instant.parse("2026-11-01T05:00:00Z")
        val nights = SunTimes.nights(start, end, lat = 41.8781, lon = -87.6298)
        assertTwoNights(start, end, nights, sunrise = "2026-10-31T12:22:00Z", sunset = "2026-10-31T22:46:00Z")
    }

    @Test fun nights_lisbonAtSummerSolstice() {
        val start = Instant.parse("2026-06-20T23:00:00Z")
        val end = Instant.parse("2026-06-21T23:00:00Z")
        val nights = SunTimes.nights(start, end, lat = 38.7223, lon = -9.1393)
        assertTwoNights(start, end, nights, sunrise = "2026-06-21T05:12:00Z", sunset = "2026-06-21T20:05:00Z")
    }

    @Test fun nights_25HourDstDay() {
        // Nov 1 2026 in Blaine, MN: clocks fall back, so local midnight to midnight is 25 hours.
        val start = Instant.parse("2026-11-01T05:00:00Z")
        val end = Instant.parse("2026-11-02T06:00:00Z")
        val nights = SunTimes.nights(start, end, lat = 45.16, lon = -93.23)
        assertTwoNights(start, end, nights, sunrise = "2026-11-01T12:52:00Z", sunset = "2026-11-01T23:01:00Z")
    }

    @Test fun nights_midnightSunHasNone() {
        val start = Instant.parse("2026-06-21T08:00:00Z")
        val nights = SunTimes.nights(start, start.plus(Duration.ofDays(1)), lat = 71.2906, lon = -156.7886)
        assertEquals(emptyList<SunTimes.Night>(), nights)
    }

    @Test fun nights_polarNightIsTheWholeDay() {
        val start = Instant.parse("2026-12-21T09:00:00Z")
        val end = start.plus(Duration.ofDays(1))
        val nights = SunTimes.nights(start, end, lat = 71.2906, lon = -156.7886)
        assertEquals(listOf(SunTimes.Night(start, end)), nights)
    }
}
