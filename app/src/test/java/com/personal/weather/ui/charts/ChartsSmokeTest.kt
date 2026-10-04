package com.personal.weather.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.weather.forecast.ForecastBuilder
import com.personal.weather.forecast.TestSnapshots
import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.NumericValue
import com.personal.weather.nws.WeatherCondition
import com.personal.weather.nws.WeatherSeries
import com.personal.weather.nws.WeatherValue
import com.personal.weather.ui.theme.ChartColors
import com.personal.weather.ui.theme.WeatherTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartsSmokeTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Chicago")

    @Test fun rendersGapsPrecipAndAmounts() {
        val start = TestSnapshots.MIDNIGHT
        val grid = GridProperties(
            temperature = TestSnapshots.hourly(start, 24) { i -> if (i in 3..5) null else 10.0 + i },
            weather = WeatherSeries(listOf(
                WeatherValue(TestSnapshots.validTime(start.plusSeconds(6 * 3600), "PT3H"), listOf(WeatherCondition("likely", "rain"), WeatherCondition("chance", "snow"))),
            )),
            snowfallAmount = NumericSeries("wmoUnit:mm", listOf(NumericValue(TestSnapshots.validTime(start.plusSeconds(6 * 3600), "PT6H"), 20.0))),
        )
        val day = ForecastBuilder.build(TestSnapshots.snapshot(grid = grid), TestSnapshots.NOW).days.first()

        compose.setContent {
            WeatherTheme {
                Column(Modifier.size(400.dp, 600.dp)) {
                    DualLineChart(
                        day = day, zone = zone,
                        series = listOf(
                            LineSeries("Temperature", ChartColors.Light.temperature, day.hours.map { it.tempF }),
                            LineSeries("Wind chill", ChartColors.Light.windChill, day.hours.map { it.windChillF }),
                        ),
                        range = ChartMath.temperatureRange(day.hours.map { it.tempF }),
                        gridStep = 10.0, axisLabel = { "${it.toInt()}°" }, valueLabel = { "${it.toInt()}°" },
                        nowFraction = 0.6f, modifier = Modifier.weight(1f),
                    )
                    LikelihoodBarChart(day = day, zone = zone, nowFraction = 0.6f, modifier = Modifier.weight(1f))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Temperature").assertExists()
        compose.onNodeWithText("Sleet").assertExists()
    }

    @Test fun rendersDstDayWithNoPrecipAtZeroSize() {
        // Nov 1 2026 is a 25-hour day in Chicago.
        val start = Instant.parse("2026-11-01T05:00:00Z")
        val now = Instant.parse("2026-11-01T18:00:00Z")
        val day = ForecastBuilder.build(
            TestSnapshots.snapshot(grid = GridProperties(temperature = TestSnapshots.hourly(start, 25) { i -> if (i == 0) null else 5.0 }), fetchedAt = now),
            now,
        ).days.first()

        compose.setContent {
            WeatherTheme {
                Column {
                    DualLineChart(
                        day = day, zone = zone, series = listOf(LineSeries("Precip potential", ChartColors.Dark.precipPotential, day.hours.map { it.popPct?.toDouble() })),
                        range = ChartMath.PERCENT_RANGE, gridStep = 20.0, axisLabel = { "${it.toInt()}%" }, valueLabel = { "${it.toInt()}%" },
                        nowFraction = null, modifier = Modifier.size(0.dp),
                    )
                    LikelihoodBarChart(day = day, zone = zone, nowFraction = null, modifier = Modifier.size(300.dp, 150.dp))
                }
            }
        }
        compose.waitForIdle()
    }
}
