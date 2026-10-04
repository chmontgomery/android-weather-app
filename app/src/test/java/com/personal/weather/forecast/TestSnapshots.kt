package com.personal.weather.forecast

import com.personal.weather.location.Place
import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.NumericValue
import com.personal.weather.nws.ObservationProperties
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Shared fixtures: a Blaine, MN snapshot whose temperature series starts at local midnight on Sat Oct 3 2026. */
object TestSnapshots {
    const val ZONE = "America/Chicago"
    val NOW: Instant = Instant.parse("2026-10-03T19:00:00Z")       // Sat 2:00 PM CDT
    val MIDNIGHT: Instant = Instant.parse("2026-10-03T05:00:00Z")  // Sat 12:00 AM CDT
    val BLAINE = Place("Blaine, MN", 45.16, -93.23)

    fun validTime(start: Instant, duration: String): String =
        start.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "/" + duration

    fun hourly(start: Instant, hours: Int, value: (Int) -> Double?): NumericSeries =
        NumericSeries("wmoUnit:degC", (0 until hours).map { NumericValue(validTime(start.plus(it.toLong(), ChronoUnit.HOURS), "PT1H"), value(it)) })

    fun grid(start: Instant = MIDNIGHT, hours: Int = 72, tempC: (Int) -> Double? = { 15.0 }): GridProperties =
        GridProperties(temperature = hourly(start, hours, tempC))

    fun snapshot(
        place: Place = BLAINE,
        fetchedAt: Instant = NOW,
        grid: GridProperties = grid(),
        observation: ObservationProperties? = null,
        zone: String = ZONE,
    ) = ForecastSnapshot(place, fetchedAt.toEpochMilli(), zone, grid, observation)
}
