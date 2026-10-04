package com.personal.weather.forecast

import com.personal.weather.forecast.TestSnapshots.MIDNIGHT
import com.personal.weather.forecast.TestSnapshots.NOW
import com.personal.weather.forecast.TestSnapshots.hourly
import com.personal.weather.forecast.TestSnapshots.snapshot
import com.personal.weather.forecast.TestSnapshots.validTime
import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.Measurement
import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.NumericValue
import com.personal.weather.nws.ObservationProperties
import com.personal.weather.nws.WeatherCondition
import com.personal.weather.nws.WeatherSeries
import com.personal.weather.nws.WeatherValue
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit.HOURS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastBuilderTest {
    private fun h(n: Long): Instant = MIDNIGHT.plus(n, HOURS)

    @Test fun build_slicesIntoCalendarDaysStartingToday() {
        val f = ForecastBuilder.build(snapshot(), NOW)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5)), f.days.map { it.date })
        f.days.forEach { assertEquals(24, it.hours.size) }
        assertEquals("Blaine, MN", f.place.name)
        assertEquals(NOW, f.fetchedAt)
    }

    @Test fun build_dropsDaysBeforeToday() {
        val f = ForecastBuilder.build(snapshot(), NOW.plus(48, HOURS))
        assertEquals(listOf(LocalDate.of(2026, 10, 5)), f.days.map { it.date })
    }

    @Test fun build_usesLocationTimeZoneNotUtc() {
        // 2026-10-04T05:00Z is Oct 3 10 PM in Los Angeles: "today" there is still Oct 3.
        val grid = GridProperties(temperature = hourly(Instant.parse("2026-10-03T07:00:00Z"), 48) { 15.0 })
        val f = ForecastBuilder.build(snapshot(grid = grid, zone = "America/Los_Angeles"), Instant.parse("2026-10-04T05:00:00Z"))
        assertEquals(LocalDate.of(2026, 10, 3), f.days.first().date)
        assertEquals(Instant.parse("2026-10-03T07:00:00Z"), f.days.first().start)
    }

    @Test fun build_hoursBeforeDataStartAreNullNotDropped() {
        // Data starts 7 AM local; today's first 7 hours are blank.
        val grid = GridProperties(temperature = hourly(h(7), 24) { 15.0 })
        val today = ForecastBuilder.build(snapshot(grid = grid), NOW).days.first()
        assertEquals(24, today.hours.size)
        assertNull(today.hours[6].tempF)
        assertEquals(59.0, today.hours[7].tempF!!, 1e-9)
    }

    @Test fun build_omitsDaysWithoutAnyTemperature() {
        // Day 2 gets only null temperatures; day 3 has a single real hour.
        val grid = GridProperties(temperature = hourly(MIDNIGHT, 49) { i -> if (i in 24..47) null else 15.0 })
        val f = ForecastBuilder.build(snapshot(grid = grid), NOW)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5)), f.days.map { it.date })
        assertEquals(1, f.days[1].hours.count { it.tempF != null })
    }

    @Test fun build_missingSeriesGiveNullValues() {
        val f = ForecastBuilder.build(snapshot(), NOW)
        val hour = f.days.first().hours.first()
        assertNull(hour.windChillF)
        assertNull(hour.popPct)
        assertNull(hour.skyPct)
        assertTrue(hour.likelihood.isEmpty())
    }

    @Test fun build_mapsAllSeries() {
        val grid = GridProperties(
            temperature = hourly(MIDNIGHT, 24) { 0.0 },
            windChill = hourly(MIDNIGHT, 24) { -10.0 },
            probabilityOfPrecipitation = NumericSeries("wmoUnit:percent", listOf(NumericValue(validTime(MIDNIGHT, "PT24H"), 42.4))),
            skyCover = NumericSeries("wmoUnit:percent", listOf(NumericValue(validTime(MIDNIGHT, "PT24H"), 87.6))),
            weather = WeatherSeries(listOf(WeatherValue(validTime(h(3), "PT2H"), listOf(WeatherCondition("likely", "snow"))))),
        )
        val day = ForecastBuilder.build(snapshot(grid = grid), NOW).days.first()
        assertEquals(32.0, day.hours[0].tempF!!, 1e-9)
        assertEquals(14.0, day.hours[0].windChillF!!, 1e-9)
        assertEquals(42, day.hours[0].popPct)
        assertEquals(88, day.hours[0].skyPct)
        assertEquals(mapOf(PrecipType.SNOW to Likelihood.LIKELY), day.hours[3].likelihood)
        assertEquals(mapOf(PrecipType.SNOW to Likelihood.LIKELY), day.hours[4].likelihood)
        assertTrue(day.hours[5].likelihood.isEmpty())
    }

    @Test fun highLow_fromHourlyTemps_todayIncludesCurrentTemp() {
        // Forecast temps on Oct 3 range 5..15 C (41..59 F); fresh observation is 20 C (68 F).
        val grid = GridProperties(temperature = hourly(MIDNIGHT, 48) { i -> if (i < 24) 5.0 + (i % 11) else 10.0 })
        val obs = ObservationProperties("2026-10-03T18:45:00+00:00", Measurement(20.0))
        val f = ForecastBuilder.build(snapshot(grid = grid, observation = obs), NOW)
        assertEquals(68, f.currentTempF)
        assertEquals(68, f.days[0].highF)
        assertEquals(41, f.days[0].lowF)
        assertEquals(50, f.days[1].highF)
        assertEquals(50, f.days[1].lowF)
    }

    @Test fun currentTemp_staleObservationFallsBackToForecast() {
        val grid = GridProperties(temperature = hourly(MIDNIGHT, 24) { i -> if (i == 14) 10.0 else 0.0 }) // 2 PM local = NOW
        val obs = ObservationProperties("2026-10-03T17:00:00+00:00", Measurement(20.0)) // 2 h old
        assertEquals(50, ForecastBuilder.build(snapshot(grid = grid, observation = obs), NOW).currentTempF)
    }

    @Test fun currentTemp_nullObservationValueFallsBackToForecast() {
        val grid = GridProperties(temperature = hourly(MIDNIGHT, 24) { i -> if (i == 14) 10.0 else 0.0 })
        val obs = ObservationProperties("2026-10-03T18:45:00+00:00", Measurement(null))
        assertEquals(50, ForecastBuilder.build(snapshot(grid = grid, observation = obs), NOW).currentTempF)
    }

    @Test fun currentTemp_noObservationNoForecastIsNull() {
        val grid = GridProperties(temperature = hourly(h(20), 24) { 10.0 }) // starts after NOW
        assertNull(ForecastBuilder.build(snapshot(grid = grid), NOW).currentTempF)
    }

    @Test fun amounts_rainNeedsRainLikelihood_snowAndIceAlwaysShown() {
        val grid = GridProperties(
            temperature = hourly(MIDNIGHT, 48) { 0.0 },
            weather = WeatherSeries(listOf(
                WeatherValue(validTime(h(6), "PT6H"), listOf(WeatherCondition("chance", "rain"))),
                WeatherValue(validTime(h(12), "PT6H"), listOf(WeatherCondition("likely", "snow"))),
            )),
            quantitativePrecipitation = NumericSeries("wmoUnit:mm", listOf(
                NumericValue(validTime(h(0), "PT6H"), 0.0),   // zero: dropped
                NumericValue(validTime(h(6), "PT6H"), 2.54),  // rain hours: kept
                NumericValue(validTime(h(12), "PT6H"), 5.0),  // snow-only hours: no rain label
            )),
            snowfallAmount = NumericSeries("wmoUnit:mm", listOf(NumericValue(validTime(h(12), "PT6H"), 38.1))),
            iceAccumulation = NumericSeries("wmoUnit:mm", listOf(NumericValue(validTime(h(18), "PT6H"), 1.27))),
        )
        val amounts = ForecastBuilder.build(snapshot(grid = grid), NOW).days.first().amounts
        assertEquals(3, amounts.size)
        val rain = amounts.single { it.type == PrecipType.RAIN }
        assertEquals(h(6), rain.start)
        assertEquals(h(12), rain.end)
        assertEquals(0.1, rain.inches, 1e-9)
        assertEquals(1.5, amounts.single { it.type == PrecipType.SNOW }.inches, 1e-9)
        assertEquals(0.05, amounts.single { it.type == PrecipType.FREEZING_RAIN }.inches, 1e-9)
    }

    @Test fun amounts_blockSpanningMidnightLabelledOnlyOnDayOfItsMidpoint() {
        val grid = GridProperties(
            temperature = hourly(MIDNIGHT, 48) { 0.0 },
            snowfallAmount = NumericSeries("wmoUnit:mm", listOf(NumericValue(validTime(h(21), "PT6H"), 25.4))),
        )
        val days = ForecastBuilder.build(snapshot(grid = grid), NOW).days
        // Block is 9 PM - 3 AM; midpoint is midnight, which belongs to the second day.
        assertEquals(0, days[0].amounts.size)
        assertEquals(1, days[1].amounts.size)
    }

    @Test fun amounts_blockMostlyBeforeMidnightStaysOnFirstDay() {
        val grid = GridProperties(
            temperature = hourly(MIDNIGHT, 48) { 0.0 },
            snowfallAmount = NumericSeries("wmoUnit:mm", listOf(NumericValue(validTime(h(18), "PT8H"), 25.4))),
        )
        val days = ForecastBuilder.build(snapshot(grid = grid), NOW).days
        assertEquals(1, days[0].amounts.size)
        assertEquals(0, days[1].amounts.size)
    }

    @Test fun build_mapsWindAndGustsToMph() {
        val grid = GridProperties(
            temperature = hourly(MIDNIGHT, 24) { 10.0 },
            windSpeed = NumericSeries("wmoUnit:km_h-1", listOf(NumericValue(validTime(h(0), "PT2H"), 16.09344), NumericValue(validTime(h(2), "PT1H"), null))),
            windGust = NumericSeries("wmoUnit:km_h-1", listOf(NumericValue(validTime(h(0), "PT1H"), 48.28032))),
        )
        val hours = ForecastBuilder.build(snapshot(grid = grid), NOW).days.first().hours
        assertEquals(10.0, hours[0].windMph!!, 1e-6)
        assertEquals(10.0, hours[1].windMph!!, 1e-6)
        assertNull(hours[2].windMph)
        assertEquals(30.0, hours[0].gustMph!!, 1e-6)
        assertNull(hours[1].gustMph)
    }

    @Test fun isToday() {
        val f = ForecastBuilder.build(snapshot(), NOW)
        assertTrue(f.isToday(f.days[0], NOW))
        assertTrue(!f.isToday(f.days[1], NOW))
    }
}
