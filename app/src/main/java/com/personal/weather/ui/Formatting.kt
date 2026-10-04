package com.personal.weather.ui

import com.personal.weather.forecast.ForecastSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** User-visible text. Status times use the phone's zone; tab dates come from the location's calendar days. */
object Formatting {
    private val tab = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayTime = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

    fun tabLabel(date: LocalDate): String = date.format(tab)

    fun updatedLabel(fetchedAt: Instant, now: Instant, zone: ZoneId): String {
        val fetched = fetchedAt.atZone(zone)
        val sameDay = fetched.toLocalDate() == now.atZone(zone).toLocalDate()
        return "Updated " + fetched.format(if (sameDay) time else dayTime)
    }

    fun refreshFailedLabel(fetchedAt: Instant, zone: ZoneId): String =
        "Couldn't refresh — showing data from " + fetchedAt.atZone(zone).format(dayTime)

    /** A temperature stored in °F, shown in [unit]. */
    fun temp(valueF: Int?, unit: TempUnit = TempUnit.F): String =
        valueF?.let { "${unit.fromF(it.toDouble()).roundToInt()}°" } ?: "—"

    /** True when wind chill sits on the same point as temperature, so only the wind chill number is shown. */
    fun tempCoveredByWindChill(tempF: Double, windChillF: Double?, unit: TempUnit = TempUnit.F): Boolean =
        windChillF != null && unit.fromF(tempF).roundToInt() == unit.fromF(windChillF).roundToInt()

    /** Precip/sky chart value label; 0% gets none (the line sits on the axis and there's no room). */
    fun percentLabel(value: Double): String? = value.roundToInt().takeIf { it != 0 }?.let { "$it%" }

    /** Who the forecast came from — shown in the ⓘ popup (Open-Meteo's licence requires credit). */
    fun sourceCredit(snapshot: ForecastSnapshot): String = when (snapshot) {
        is ForecastSnapshot.Nws -> "weather.gov"
        is ForecastSnapshot.OpenMeteo -> "Open-Meteo.com"
    }
}
