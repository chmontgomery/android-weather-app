package com.personal.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.personal.weather.forecast.PrecipType

/**
 * Every chart sits on a light card in both themes, white by day and gray by night (as on forecast.weather.gov), so the
 * series colors are one set, dark enough for value labels to read on either shade. Only the day tabs follow the theme.
 */
@Immutable
data class ChartColors(
    val temperature: Color,
    val windChill: Color,
    val precipPotential: Color,
    val skyCover: Color,
    val wind: Color,
    val gust: Color,
    val rain: Color,
    val snow: Color,
    val freezingRain: Color,
    val sleet: Color,
    val grid: Color,
    val axisText: Color,
    val now: Color,
    /** Chart card background while the sun is up. */
    val panelDay: Color,
    /** Bands over the chart card while the sun is down. */
    val panelNight: Color,
    /** Legend labels on the chart card. */
    val panelText: Color,
    /** Day tab text and the selected tab's outline. */
    val dayTab: Color,
) {
    fun precip(type: PrecipType): Color = when (type) {
        PrecipType.RAIN -> rain
        PrecipType.SNOW -> snow
        PrecipType.FREEZING_RAIN -> freezingRain
        PrecipType.SLEET -> sleet
    }

    companion object {
        val Light = ChartColors(
            temperature = Color(0xFFB71C1C),
            windChill = Color(0xFF1546B8),
            precipPotential = Color(0xFF1B6420),
            skyCover = Color(0xFF7A4310),
            wind = Color(0xFF8E24AA),
            gust = Color(0xFF311B92),
            rain = Color(0xFF23702A),
            snow = Color(0xFF0064A8),
            freezingRain = Color(0xFFAD1F8F),
            sleet = Color(0xFFA84300),
            grid = Color(0xFFABABAB),
            axisText = Color(0xFF4A4A4A),
            now = Color(0xFF212121),
            panelDay = Color(0xFFFFFFFF),
            panelNight = Color(0xFFE0E0E0),
            panelText = Color(0xFF212121),
            dayTab = Color(0xFF424242),
        )
        val Dark = Light.copy(dayTab = Color(0xFFBDBDBD))
    }
}

val LocalChartColors = staticCompositionLocalOf { ChartColors.Light }

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalChartColors provides if (dark) ChartColors.Dark else ChartColors.Light, content = content)
    }
}
