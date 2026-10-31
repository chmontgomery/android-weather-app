package com.personal.weather.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Lines, bars and their value labels sit on the day and night panel in both themes, so all must read on both. */
    @Test fun seriesColorsMeetTextContrastOnBothPanelShades() {
        for ((theme, c) in listOf("Light" to ChartColors.Light, "Dark" to ChartColors.Dark)) {
            val series = mapOf(
                "temperature" to c.temperature, "windChill" to c.windChill, "precipPotential" to c.precipPotential,
                "skyCover" to c.skyCover, "wind" to c.wind, "gust" to c.gust, "rain" to c.rain, "snow" to c.snow,
                "freezingRain" to c.freezingRain, "sleet" to c.sleet, "axisText" to c.axisText, "now" to c.now,
            )
            for ((name, color) in series) {
                for ((shade, bg) in listOf("day" to c.panelDay, "night" to c.panelNight)) {
                    val ratio = contrast(color, bg)
                    assertTrue("$theme.$name on $shade panel is $ratio:1", ratio >= 4.5f)
                }
            }
        }
    }
}
