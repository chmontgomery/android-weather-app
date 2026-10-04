package com.personal.weather.ui.charts

import com.personal.weather.forecast.AmountBlock
import com.personal.weather.forecast.Likelihood
import com.personal.weather.forecast.PrecipType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit.HOURS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    private val zone = ZoneId.of("America/Chicago")
    private val dayStart = Instant.parse("2026-10-03T05:00:00Z")
    private val dayEnd = Instant.parse("2026-10-04T05:00:00Z")

    @Test fun temperatureRange_roundsToTensWithMinSpan20() {
        assertEquals(ChartMath.YRange(60.0, 80.0), ChartMath.temperatureRange(listOf(61.0, 65.0)))
        assertEquals(ChartMath.YRange(40.0, 80.0), ChartMath.temperatureRange(listOf(41.0, null, 72.0)))
        assertEquals(ChartMath.YRange(-10.0, 20.0), ChartMath.temperatureRange(listOf(-5.0, 12.0)))
        assertEquals(ChartMath.YRange(70.0, 90.0), ChartMath.temperatureRange(listOf(70.0)))
        assertEquals(ChartMath.YRange(40.0, 80.0), ChartMath.temperatureRange(listOf(40.0, 80.0)))
    }

    @Test fun temperatureRange_allNullUsesDefault() {
        assertEquals(ChartMath.YRange(30.0, 80.0), ChartMath.temperatureRange(listOf(null, null)))
        assertEquals(ChartMath.YRange(30.0, 80.0), ChartMath.temperatureRange(emptyList()))
    }

    @Test fun windAxis_startsAtZeroInStepsOf5Or10() {
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 40.0), 10.0), ChartMath.windAxis(listOf(12.0, null, 32.0)))
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 20.0), 5.0), ChartMath.windAxis(listOf(17.0)))
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 20.0), 5.0), ChartMath.windAxis(listOf(20.0)))
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 30.0), 10.0), ChartMath.windAxis(listOf(20.5)))
    }

    @Test fun windAxis_calmOrMissingStillHasTwoSteps() {
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 10.0), 5.0), ChartMath.windAxis(listOf(0.0, 3.0)))
        assertEquals(ChartMath.Axis(ChartMath.YRange(0.0, 10.0), 5.0), ChartMath.windAxis(listOf(null)))
    }

    @Test fun gridLines() {
        assertEquals(listOf(40.0, 50.0, 60.0, 70.0, 80.0), ChartMath.gridLines(ChartMath.YRange(40.0, 80.0), 10.0))
        assertEquals(6, ChartMath.gridLines(ChartMath.PERCENT_RANGE, 20.0).size)
    }

    @Test fun xFraction_normalDay() {
        assertEquals(0f, ChartMath.xFraction(dayStart, dayStart, dayEnd), 1e-6f)
        assertEquals(0.5f, ChartMath.xFraction(dayStart.plus(12, HOURS), dayStart, dayEnd), 1e-6f)
        assertEquals(1f, ChartMath.xFraction(dayEnd, dayStart, dayEnd), 1e-6f)
    }

    @Test fun xFraction_fallBackDayUsesRealElapsedTime() {
        val date = LocalDate.of(2026, 11, 1)
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val localNoon = date.atTime(12, 0).atZone(zone).toInstant() // 13 real hours after midnight
        assertEquals(13f / 25f, ChartMath.xFraction(localNoon, start, end), 1e-6f)
    }

    @Test fun yFraction_clampsToRange() {
        val r = ChartMath.YRange(40.0, 80.0)
        assertEquals(0.5f, ChartMath.yFraction(60.0, r), 1e-6f)
        assertEquals(0f, ChartMath.yFraction(10.0, r), 1e-6f)
        assertEquals(1f, ChartMath.yFraction(100.0, r), 1e-6f)
    }

    @Test fun hourLabels() {
        assertEquals("12a", ChartMath.hourLabel(dayStart, zone))
        assertEquals("3a", ChartMath.hourLabel(dayStart.plus(3, HOURS), zone))
        assertEquals("12p", ChartMath.hourLabel(dayStart.plus(12, HOURS), zone))
        assertEquals("3p", ChartMath.hourLabel(dayStart.plus(15, HOURS), zone))
        assertTrue(ChartMath.isLabeledHour(dayStart.plus(21, HOURS), zone))
        assertFalse(ChartMath.isLabeledHour(dayStart.plus(22, HOURS), zone))
    }

    @Test fun amountLabelX_centersOnVisiblePart() {
        val inside = AmountBlock(dayStart.plus(6, HOURS), dayStart.plus(12, HOURS), PrecipType.RAIN, 0.1)
        assertEquals(9f / 24f, ChartMath.amountLabelX(inside, dayStart, dayEnd), 1e-6f)
        val crossesMidnight = AmountBlock(dayStart.plus(21, HOURS), dayStart.plus(27, HOURS), PrecipType.SNOW, 1.0)
        assertEquals(22.5f / 24f, ChartMath.amountLabelX(crossesMidnight, dayStart, dayEnd), 1e-6f)
    }

    @Test fun amountText() {
        fun block(type: PrecipType, inches: Double) = AmountBlock(dayStart, dayEnd, type, inches)
        assertEquals("0.12\"", ChartMath.amountText(block(PrecipType.RAIN, 0.123)))
        assertEquals("<0.01\"", ChartMath.amountText(block(PrecipType.RAIN, 0.004)))
        assertEquals("1.5\" snow", ChartMath.amountText(block(PrecipType.SNOW, 1.54)))
        assertEquals("0.05\" ice", ChartMath.amountText(block(PrecipType.FREEZING_RAIN, 0.05)))
    }

    @Test fun barHeights() {
        assertEquals(0.25f, ChartMath.barHeightFraction(Likelihood.SLIGHT_CHANCE), 1e-6f)
        assertEquals(1f, ChartMath.barHeightFraction(Likelihood.OCCASIONAL), 1e-6f)
    }
}
