package com.personal.weather.forecast

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DaySlicerTest {
    private val chicago = ZoneId.of("America/Chicago")

    @Test fun normalDayHas24Hours() {
        val hours = DaySlicer.hoursOf(LocalDate.of(2026, 10, 3), chicago)
        assertEquals(24, hours.size)
        assertEquals(Instant.parse("2026-10-03T05:00:00Z"), hours.first())
        assertEquals(Instant.parse("2026-10-04T04:00:00Z"), hours.last())
    }

    @Test fun fallBackDayHas25Hours() {
        assertEquals(25, DaySlicer.hoursOf(LocalDate.of(2026, 11, 1), chicago).size)
    }

    @Test fun springForwardDayHas23Hours() {
        assertEquals(23, DaySlicer.hoursOf(LocalDate.of(2026, 3, 8), chicago).size)
    }

    @Test fun dateOfUsesGivenZone() {
        val t = Instant.parse("2026-10-04T03:00:00Z") // Oct 3 10 PM CDT, Oct 3 8 PM PDT, Oct 4 in UTC
        assertEquals(LocalDate.of(2026, 10, 3), DaySlicer.dateOf(t, chicago))
        assertEquals(LocalDate.of(2026, 10, 3), DaySlicer.dateOf(t, ZoneId.of("America/Los_Angeles")))
        assertEquals(LocalDate.of(2026, 10, 4), DaySlicer.dateOf(t, ZoneId.of("UTC")))
    }
}
