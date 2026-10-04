package com.personal.weather.forecast

import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.ObservationProperties
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object ForecastBuilder {
    const val OBSERVATION_MAX_AGE_MINUTES = 90L

    fun build(snapshot: ForecastSnapshot, now: Instant): Forecast {
        val zone = ZoneId.of(snapshot.timeZone)
        val g = snapshot.grid
        val temp = TimeSeriesExpander.numeric(g.temperature, Units.toFahrenheit(g.temperature.uom))
        val chill = TimeSeriesExpander.numeric(g.windChill, Units.toFahrenheit(g.windChill.uom))
        val pop = TimeSeriesExpander.numeric(g.probabilityOfPrecipitation)
        val sky = TimeSeriesExpander.numeric(g.skyCover)
        val wind = TimeSeriesExpander.numeric(g.windSpeed, Units.toMph(g.windSpeed.uom))
        val gust = TimeSeriesExpander.numeric(g.windGust, Units.toMph(g.windGust.uom))
        val weather = TimeSeriesExpander.expandHourly(g.weather.values.map { it.validTime to LikelihoodMapper.map(it.value) })

        val currentTemp = observedTempF(snapshot.observation, now) ?: temp[now.truncatedTo(ChronoUnit.HOURS)]
        val today = DaySlicer.dateOf(now, zone)
        val dates = temp.filterValues { it != null }.keys
            .map { DaySlicer.dateOf(it, zone) }
            .filter { it >= today }
            .toSortedSet()
        val blocks = amountBlocks(g, weather)

        val days = dates.map { date ->
            val (start, end) = DaySlicer.bounds(date, zone)
            val hours = DaySlicer.hoursOf(date, zone).map { h ->
                HourPoint(h, temp[h], chill[h], pop[h]?.roundToInt(), sky[h]?.roundToInt(), weather[h] ?: emptyMap(), wind[h], gust[h])
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
                amounts = blocks.filter { val mid = it.start.plus(Duration.between(it.start, it.end).dividedBy(2)); mid >= start && mid < end },
            )
        }
        return Forecast(snapshot.place, zone, snapshot.fetchedAt, currentTemp?.roundToInt(), days)
    }

    private fun observedTempF(obs: ObservationProperties?, now: Instant): Double? {
        val celsius = obs?.temperature?.value ?: return null
        val at = runCatching { OffsetDateTime.parse(obs.timestamp).toInstant() }.getOrNull() ?: return null
        if (Duration.between(at, now).toMinutes() > OBSERVATION_MAX_AGE_MINUTES) return null
        return Units.cToF(celsius)
    }

    /**
     * Non-zero amount blocks. Rain uses QPF (liquid equivalent) and is only labeled when the block has a rain
     * likelihood, so snow-only periods don't get a misleading rain label. Sleet has no NWS amount series.
     */
    private fun amountBlocks(g: GridProperties, weather: Map<Instant, Map<PrecipType, Likelihood>>): List<AmountBlock> {
        fun blocks(series: NumericSeries, type: PrecipType, keep: (Instant, Instant) -> Boolean): List<AmountBlock> =
            series.values.mapNotNull { v ->
                val mm = v.value ?: return@mapNotNull null
                if (mm <= 0.0) return@mapNotNull null
                val (start, duration) = TimeSeriesExpander.parseValidTime(v.validTime)
                val end = start.plus(duration)
                if (keep(start, end)) AmountBlock(start, end, type, Units.mmToInches(mm)) else null
            }

        val hasRain = { start: Instant, end: Instant ->
            weather.any { (hour, types) -> hour >= start && hour < end && PrecipType.RAIN in types }
        }
        val always = { _: Instant, _: Instant -> true }
        return blocks(g.quantitativePrecipitation, PrecipType.RAIN, hasRain) +
            blocks(g.snowfallAmount, PrecipType.SNOW, always) +
            blocks(g.iceAccumulation, PrecipType.FREEZING_RAIN, always)
    }
}
