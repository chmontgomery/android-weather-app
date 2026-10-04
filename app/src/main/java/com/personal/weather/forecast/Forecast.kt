package com.personal.weather.forecast

import com.personal.weather.location.Place
import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.ObservationProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** Raw NWS data from one successful fetch. This is what gets cached; [ForecastBuilder] derives everything else. */
@Serializable
data class ForecastSnapshot(
    val place: Place,
    val fetchedAtEpochMs: Long,
    val timeZone: String,
    val grid: GridProperties,
    val observation: ObservationProperties? = null,
) {
    val fetchedAt: Instant get() = Instant.ofEpochMilli(fetchedAtEpochMs)
}

data class HourPoint(
    val start: Instant,
    val tempF: Double?,
    val windChillF: Double?,
    val popPct: Int?,
    val skyPct: Int?,
    val likelihood: Map<PrecipType, Likelihood>,
    val windMph: Double? = null,
    val gustMph: Double? = null,
)

/** A forecast precipitation amount over an NWS interval (usually 6 hours). */
data class AmountBlock(val start: Instant, val end: Instant, val type: PrecipType, val inches: Double)

/** One calendar day, midnight to midnight in the location's zone. [hours] covers every hour of the day; missing data is null. */
data class DayForecast(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val hours: List<HourPoint>,
    val highF: Int?,
    val lowF: Int?,
    val amounts: List<AmountBlock>,
)

data class Forecast(
    val place: Place,
    val timeZone: ZoneId,
    val fetchedAt: Instant,
    val currentTempF: Int?,
    val days: List<DayForecast>,
) {
    fun isToday(day: DayForecast, now: Instant): Boolean = day.date == DaySlicer.dateOf(now, timeZone)
}
