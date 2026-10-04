package com.personal.weather.forecast

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar-day math in a specific zone. DST days have 23 or 25 hours. */
object DaySlicer {
    fun bounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

    fun hoursOf(date: LocalDate, zone: ZoneId): List<Instant> {
        val (start, end) = bounds(date, zone)
        return generateSequence(start) { it.plus(1, ChronoUnit.HOURS) }.takeWhile { it < end }.toList()
    }

    fun dateOf(instant: Instant, zone: ZoneId): LocalDate = LocalDate.ofInstant(instant, zone)
}
