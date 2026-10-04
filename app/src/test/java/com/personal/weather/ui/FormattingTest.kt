package com.personal.weather.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    private val zone = ZoneId.of("America/Chicago")
    private val fetched = Instant.parse("2026-10-03T19:14:00Z") // Sat 2:14 PM CDT

    @Test fun tabLabel() {
        assertEquals("Sun Oct 4", Formatting.tabLabel(LocalDate.of(2026, 10, 4)))
    }

    @Test fun updatedLabel_sameDayOmitsWeekday() {
        assertEquals("Updated 2:14 PM", Formatting.updatedLabel(fetched, fetched.plusSeconds(600), zone))
    }

    @Test fun updatedLabel_otherDayIncludesWeekday() {
        assertEquals("Updated Sat 2:14 PM", Formatting.updatedLabel(fetched, fetched.plusSeconds(86_400), zone))
    }

    @Test fun refreshFailedLabel() {
        assertEquals("Couldn't refresh — showing data from Sat 2:14 PM", Formatting.refreshFailedLabel(fetched, zone))
    }

    @Test fun percentLabel_hidesZero() {
        assertEquals("53%", Formatting.percentLabel(53.0))
        assertEquals("1%", Formatting.percentLabel(0.6))
        assertEquals(null, Formatting.percentLabel(0.0))
        assertEquals(null, Formatting.percentLabel(0.4))
    }

    @Test fun tempLabelHiddenWhenWindChillIsSamePoint() {
        assertEquals(true, Formatting.tempCoveredByWindChill(48.0, 48.3))
        assertEquals(true, Formatting.tempCoveredByWindChill(47.6, 48.4))
        assertEquals(false, Formatting.tempCoveredByWindChill(48.0, 45.0))
        assertEquals(false, Formatting.tempCoveredByWindChill(48.0, null))
    }

    @Test fun temp() {
        assertEquals("72°", Formatting.temp(72))
        assertEquals("-3°", Formatting.temp(-3))
        assertEquals("—", Formatting.temp(null))
    }
}
