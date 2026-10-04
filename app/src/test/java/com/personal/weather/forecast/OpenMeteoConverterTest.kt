package com.personal.weather.forecast

import com.personal.weather.location.Country
import com.personal.weather.location.Place
import com.personal.weather.openmeteo.OpenMeteoCurrent
import com.personal.weather.openmeteo.OpenMeteoHourly
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoConverterTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val place = Place("Lagos, Faro", 37.1, -8.67, Country.PORTUGAL)
    private val midnight: Instant = LocalDate.of(2026, 10, 4).atStartOfDay(lisbon).toInstant()

    private fun hours(n: Int, from: Instant = midnight) = (0 until n).map { from.epochSecond + it * 3600L }

    private fun snap(hourly: OpenMeteoHourly, current: OpenMeteoCurrent? = null) =
        ForecastSnapshot.OpenMeteo(place, 0, "Europe/Lisbon", hourly, current)

    @Test fun windChill_nwsFormulaOnlyWhenColdAndWindy() {
        assertEquals(17.4, OpenMeteoConverter.windChillF(30.0, 20.0)!!, 0.1)   // NWS chart: 30°F, 20 mph → 17
        assertEquals(36.5, OpenMeteoConverter.windChillF(40.0, 5.0)!!, 0.1)    // NWS chart: 40°F, 5 mph → 36
        assertNull(OpenMeteoConverter.windChillF(51.0, 20.0))                  // too warm
        assertNull(OpenMeteoConverter.windChillF(30.0, 3.0))                   // too calm
        assertNull(OpenMeteoConverter.windChillF(null, 10.0))
    }

    @Test fun likelihood_thresholds() {
        assertNull(OpenMeteoConverter.likelihoodOf(14.0))
        assertEquals(Likelihood.SLIGHT_CHANCE, OpenMeteoConverter.likelihoodOf(15.0))
        assertEquals(Likelihood.SLIGHT_CHANCE, OpenMeteoConverter.likelihoodOf(24.0))
        assertEquals(Likelihood.CHANCE, OpenMeteoConverter.likelihoodOf(25.0))
        assertEquals(Likelihood.CHANCE, OpenMeteoConverter.likelihoodOf(54.0))
        assertEquals(Likelihood.LIKELY, OpenMeteoConverter.likelihoodOf(55.0))
        assertEquals(Likelihood.LIKELY, OpenMeteoConverter.likelihoodOf(74.0))
        assertEquals(Likelihood.OCCASIONAL, OpenMeteoConverter.likelihoodOf(75.0))
        assertNull(OpenMeteoConverter.likelihoodOf(null))
    }

    @Test fun type_fromWeatherCodeWithTemperatureFallback() {
        listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99).forEach { assertEquals(PrecipType.RAIN, OpenMeteoConverter.typeOf(it, 60.0)) }
        listOf(56, 57, 66, 67).forEach { assertEquals(PrecipType.FREEZING_RAIN, OpenMeteoConverter.typeOf(it, 30.0)) }
        listOf(71, 73, 75, 77, 85, 86).forEach { assertEquals(PrecipType.SNOW, OpenMeteoConverter.typeOf(it, 30.0)) }
        assertEquals(PrecipType.RAIN, OpenMeteoConverter.typeOf(3, 35.0))   // no precip code: by temperature
        assertEquals(PrecipType.SNOW, OpenMeteoConverter.typeOf(3, 34.0))
        assertEquals(PrecipType.RAIN, OpenMeteoConverter.typeOf(null, null))
    }

    @Test fun convert_mapsHourlyValues() {
        val h = OpenMeteoHourly(
            time = hours(2),
            temperature = listOf(30.0, 60.0),
            precipitationProbability = listOf(60.0, 10.0),
            cloudCover = listOf(87.6, 0.0),
            windSpeed = listOf(20.0, 4.0),
            windGusts = listOf(31.0, 9.0),
            weatherCode = listOf(73, 1),
        )
        val d = OpenMeteoConverter.convert(snap(h))
        val first = d.hours.getValue(midnight)
        assertEquals(30.0, first.tempF!!, 0.0)
        assertTrue(first.windChillF!! < 30.0)
        assertEquals(60, first.popPct)
        assertEquals(88, first.skyPct)
        assertEquals(20.0, first.windMph!!, 0.0)
        assertEquals(31.0, first.gustMph!!, 0.0)
        assertEquals(mapOf(PrecipType.SNOW to Likelihood.LIKELY), first.likelihood)
        val second = d.hours.getValue(midnight.plusSeconds(3600))
        assertNull(second.windChillF)
        assertTrue(second.likelihood.isEmpty())
    }

    @Test fun convert_nullArrayEntriesAreGaps() {
        val h = OpenMeteoHourly(time = hours(2), temperature = listOf(null, 61.0), windGusts = listOf(null, null))
        val d = OpenMeteoConverter.convert(snap(h))
        assertNull(d.hours.getValue(midnight).tempF)
        assertNull(d.hours.getValue(midnight).gustMph)
        assertEquals(61.0, d.hours.getValue(midnight.plusSeconds(3600)).tempF!!, 0.0)
    }

    @Test fun current_isPassedThrough() {
        val d = OpenMeteoConverter.convert(snap(OpenMeteoHourly(time = hours(1), temperature = listOf(60.0)), OpenMeteoCurrent(midnight.epochSecond + 900, 61.5)))
        assertEquals(61.5, d.currentTempF!!, 0.0)
        assertEquals(midnight.plusSeconds(900), d.currentTempAt)
    }

    @Test fun amounts_sumIntoLocalSixHourBlocks() {
        val h = OpenMeteoHourly(
            time = hours(12),
            temperature = List(12) { 60.0 },
            rain = List(12) { i -> if (i in 2..7) 0.02 else 0.0 },
            showers = List(12) { i -> if (i == 7) 0.01 else 0.0 },
            weatherCode = List(12) { 61 },
        )
        val amounts = OpenMeteoConverter.convert(snap(h)).amounts
        assertEquals(2, amounts.size)
        val first = amounts.single { it.start == midnight }
        assertEquals(midnight.plusSeconds(6 * 3600), first.end)
        assertEquals(PrecipType.RAIN, first.type)
        assertEquals(0.08, first.inches, 1e-9)
        assertEquals(0.05, amounts.single { it.start == midnight.plusSeconds(6 * 3600) }.inches, 1e-9)
    }

    @Test fun amounts_freezingAndSnowSeparately() {
        val h = OpenMeteoHourly(
            time = hours(6),
            temperature = List(6) { 31.0 },
            rain = listOf(0.1, 0.1, 0.0, 0.0, 0.0, 0.0),
            snowfall = listOf(0.0, 0.0, 0.5, 0.5, 0.0, 0.0),
            weatherCode = listOf(66, 66, 73, 73, 3, 3),
        )
        val amounts = OpenMeteoConverter.convert(snap(h)).amounts
        assertEquals(0.2, amounts.single { it.type == PrecipType.FREEZING_RAIN }.inches, 1e-9)
        assertEquals(1.0, amounts.single { it.type == PrecipType.SNOW }.inches, 1e-9)
        assertTrue(amounts.none { it.type == PrecipType.RAIN })
    }

    @Test fun amounts_blocksAlignToLocalSixHoursAcrossFallBack() {
        // 2026-10-25 in Lisbon has 25 hours (clocks go back at 02:00 → 01:00).
        val day: Instant = LocalDate.of(2026, 10, 25).atStartOfDay(lisbon).toInstant()
        val h = OpenMeteoHourly(time = hours(25, day), temperature = List(25) { 60.0 }, rain = List(25) { 0.01 }, weatherCode = List(25) { 61 })
        val blocks = OpenMeteoConverter.convert(snap(h)).amounts.sortedBy { it.start }
        val localStarts = blocks.map { it.start.atZone(lisbon).toLocalTime().hour }
        assertEquals(listOf(0, 6, 12, 18), localStarts)
        assertEquals(0.07, blocks.first().inches, 1e-9) // 00:00–06:00 local holds 7 real hours that day
        assertEquals(0.25, blocks.sumOf { it.inches }, 1e-9)
    }

    @Test fun amounts_traceTotalsAreDropped() {
        val h = OpenMeteoHourly(
            time = hours(12),
            temperature = List(12) { 60.0 },
            rain = List(12) { i -> if (i < 6) 0.0008 else 0.002 },        // block 1: 0.0048 (dropped); block 2: 0.012 (kept)
            snowfall = List(12) { i -> if (i < 6) 0.008 else 0.0 },       // block 1 snow: 0.048 (dropped)
            weatherCode = List(12) { 61 },
        )
        val amounts = OpenMeteoConverter.convert(snap(h)).amounts
        assertEquals(1, amounts.size)
        assertEquals(PrecipType.RAIN, amounts.single().type)
        assertEquals(midnight.plusSeconds(6 * 3600), amounts.single().start)
        assertEquals(0.012, amounts.single().inches, 1e-9)
    }

    @Test fun build_portugueseSnapshotProducesDays() {
        val h = OpenMeteoHourly(time = hours(48), temperature = List(48) { 60.0 + it % 24 })
        val f = ForecastBuilder.build(snap(h), midnight.plusSeconds(10 * 3600))
        assertEquals(listOf(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5)), f.days.map { it.date })
        assertEquals(83, f.days[0].highF)
    }
}
