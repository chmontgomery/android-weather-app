package com.personal.weather.forecast

import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.NumericSeries
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** weather.gov gridpoint data → hourly values. */
object NwsConverter {
    fun convert(s: ForecastSnapshot.Nws): HourlyData {
        val g = s.grid
        val temp = TimeSeriesExpander.numeric(g.temperature, Units.toFahrenheit(g.temperature.uom))
        val chill = TimeSeriesExpander.numeric(g.windChill, Units.toFahrenheit(g.windChill.uom))
        val pop = TimeSeriesExpander.numeric(g.probabilityOfPrecipitation)
        val sky = TimeSeriesExpander.numeric(g.skyCover)
        val wind = TimeSeriesExpander.numeric(g.windSpeed, Units.toMph(g.windSpeed.uom))
        val gust = TimeSeriesExpander.numeric(g.windGust, Units.toMph(g.windGust.uom))
        val weather = TimeSeriesExpander.expandHourly(g.weather.values.map { it.validTime to LikelihoodMapper.map(it.value) })

        val keys = temp.keys + chill.keys + pop.keys + sky.keys + wind.keys + gust.keys + weather.keys
        val hours = keys.associateWith { h ->
            HourValues(temp[h], chill[h], pop[h]?.roundToInt(), sky[h]?.roundToInt(), weather[h] ?: emptyMap(), wind[h], gust[h])
        }
        val obs = s.observation
        val obsAt = obs?.let { runCatching { OffsetDateTime.parse(it.timestamp).toInstant() }.getOrNull() }
        val obsF = obs?.temperature?.value?.let(Units::cToF)
        return HourlyData(hours, amountBlocks(g, weather), obsF?.takeIf { obsAt != null }, obsAt)
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
