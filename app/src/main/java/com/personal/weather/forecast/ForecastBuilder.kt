package com.personal.weather.forecast

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object ForecastBuilder {
    const val OBSERVATION_MAX_AGE_MINUTES = 90L

    fun build(snapshot: ForecastSnapshot, now: Instant): Forecast {
        val zone = ZoneId.of(snapshot.timeZone)
        val data = when (snapshot) {
            is ForecastSnapshot.Nws -> NwsConverter.convert(snapshot)
            is ForecastSnapshot.OpenMeteo -> OpenMeteoConverter.convert(snapshot)
        }

        val fresh = data.currentTempF?.takeIf {
            val at = data.currentTempAt ?: return@takeIf false
            Duration.between(at, now).toMinutes() <= OBSERVATION_MAX_AGE_MINUTES
        }
        val currentTemp = fresh ?: data.hours[now.truncatedTo(ChronoUnit.HOURS)]?.tempF
        val today = DaySlicer.dateOf(now, zone)
        val dates = data.hours.filterValues { it.tempF != null }.keys
            .map { DaySlicer.dateOf(it, zone) }
            .filter { it >= today }
            .toSortedSet()

        val days = dates.map { date ->
            val (start, end) = DaySlicer.bounds(date, zone)
            val hours = DaySlicer.hoursOf(date, zone).map { h ->
                val v = data.hours[h]
                HourPoint(h, v?.tempF, v?.windChillF, v?.popPct, v?.skyPct, v?.likelihood ?: emptyMap(), v?.windMph, v?.gustMph)
            }
            val temps = hours.mapNotNull { it.tempF } + listOfNotNull(currentTemp.takeIf { date == today })
            DayForecast(
                date = date,
                start = start,
                end = end,
                hours = hours,
                highF = temps.maxOrNull()?.roundToInt(),
                lowF = temps.minOrNull()?.roundToInt(),
                // Attach each block only to the day holding its midpoint, so a block crossing midnight isn't double counted.
                amounts = data.amounts.filter { val mid = it.start.plus(Duration.between(it.start, it.end).dividedBy(2)); mid >= start && mid < end },
            )
        }
        return Forecast(snapshot.place, zone, snapshot.fetchedAt, currentTemp?.roundToInt(), days)
    }
}
