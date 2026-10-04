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

/** Series colors echo forecast.weather.gov; the dark set is lightened to keep contrast on a dark background. */
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
            temperature = Color(0xFFD32F2F),
            windChill = Color(0xFF1E5BD8),
            precipPotential = Color(0xFF1B5E20),
            skyCover = Color(0xFF8D5524),
            wind = Color(0xFFB39DDB),
            gust = Color(0xFF4A148C),
            rain = Color(0xFF2E9E3E),
            snow = Color(0xFF1FA2E0),
            freezingRain = Color(0xFFD25FBF),
            sleet = Color(0xFFF07F12),
            grid = Color(0xFFCFCFCF),
            axisText = Color(0xFF555555),
            now = Color(0xFF222222),
            dayTab = Color(0xFF424242),
        )
        val Dark = ChartColors(
            temperature = Color(0xFFFF6B6B),
            windChill = Color(0xFF7EA6FF),
            precipPotential = Color(0xFF66BB6A),
            skyCover = Color(0xFFD9A066),
            wind = Color(0xFFD1C4E9),
            gust = Color(0xFFA57BE0),
            rain = Color(0xFF5BD06B),
            snow = Color(0xFF6CCBFA),
            freezingRain = Color(0xFFF08FDF),
            sleet = Color(0xFFFFA64D),
            grid = Color(0xFF444444),
            axisText = Color(0xFFBBBBBB),
            now = Color(0xFFEEEEEE),
            dayTab = Color(0xFFBDBDBD),
        )
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
