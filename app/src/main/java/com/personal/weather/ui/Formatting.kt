package com.personal.weather.ui

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

    fun temp(value: Int?): String = value?.let { "$it°" } ?: "—"

    /** True when wind chill sits on the same point as temperature, so only the wind chill number is shown. */
    fun tempCoveredByWindChill(tempF: Double, windChillF: Double?): Boolean =
        windChillF != null && tempF.roundToInt() == windChillF.roundToInt()

    /** Precip/sky chart value label; 0% gets none (the line sits on the axis and there's no room). */
    fun percentLabel(value: Double): String? = value.roundToInt().takeIf { it != 0 }?.let { "$it%" }
}
