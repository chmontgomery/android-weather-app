package com.personal.weather.forecast

import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/** Open-Meteo hourly data → hourly values in the same shape weather.gov data produces. */
object OpenMeteoConverter {
    // Minimum thresholds for creating precipitation amount blocks; trace amounts below these are dropped
    private const val RAIN_FREEZING_THRESHOLD = 0.005  // inches (displays as ≥ 0.01")
    private const val SNOW_THRESHOLD = 0.05  // inches (displays as ≥ 0.1")
    fun convert(s: ForecastSnapshot.OpenMeteo): HourlyData {
        val h = s.hourly
        val zone = ZoneId.of(s.timeZone)
        fun <T> at(list: List<T?>, i: Int): T? = list.getOrNull(i)

        val hours = h.time.indices.associate { i ->
            val tempF = at(h.temperature, i)
            val wind = at(h.windSpeed, i)
            val pop = at(h.precipitationProbability, i)
            val level = likelihoodOf(pop)
            val likelihood = if (level == null) emptyMap() else mapOf(typeOf(at(h.weatherCode, i), tempF) to level)
            Instant.ofEpochSecond(h.time[i]) to HourValues(
                tempF = tempF,
                windChillF = windChillF(tempF, wind),
                popPct = pop?.roundToInt(),
                skyPct = at(h.cloudCover, i)?.roundToInt(),
                likelihood = likelihood,
                windMph = wind,
                gustMph = at(h.windGusts, i),
            )
        }
        val current = s.current
        return HourlyData(
            hours = hours,
            amounts = amountBlocks(s, zone),
            currentTempF = current?.temperature,
            currentTempAt = current?.let { Instant.ofEpochSecond(it.time) },
        )
    }

    /** NWS wind chill (°F, mph); defined only at or below 50°F with wind above 3 mph. */
    fun windChillF(tempF: Double?, windMph: Double?): Double? {
        if (tempF == null || windMph == null || tempF > 50.0 || windMph <= 3.0) return null
        val v = windMph.pow(0.16)
        return 35.74 + 0.6215 * tempF - 35.75 * v + 0.4275 * tempF * v
    }

    /** Precipitation probability → the NWS likelihood levels the chart uses; under 15% shows no bar. */
    fun likelihoodOf(pct: Double?): Likelihood? = when {
        pct == null || pct < 15.0 -> null
        pct < 25.0 -> Likelihood.SLIGHT_CHANCE
        pct < 55.0 -> Likelihood.CHANCE
        pct < 75.0 -> Likelihood.LIKELY
        else -> Likelihood.OCCASIONAL
    }

    /** WMO weather code → precip type; a non-precip code falls back to rain above 34°F, else snow. */
    fun typeOf(code: Int?, tempF: Double?): PrecipType = when (code) {
        51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99 -> PrecipType.RAIN
        56, 57, 66, 67 -> PrecipType.FREEZING_RAIN
        71, 73, 75, 77, 85, 86 -> PrecipType.SNOW
        else -> if (tempF == null || tempF > 34.0) PrecipType.RAIN else PrecipType.SNOW
    }

    fun isFreezing(code: Int?): Boolean = code in setOf(56, 57, 66, 67)

    /** Totals per 6-hour block starting at local 00/06/12/18, so blocks never cross local midnight. */
    private fun amountBlocks(s: ForecastSnapshot.OpenMeteo, zone: ZoneId): List<AmountBlock> {
        val h = s.hourly
        data class Totals(var rain: Double = 0.0, var ice: Double = 0.0, var snow: Double = 0.0)
        val byBlock = sortedMapOf<java.time.LocalDateTime, Totals>()
        h.time.indices.forEach { i ->
            val local = Instant.ofEpochSecond(h.time[i]).atZone(zone).toLocalDateTime()
            val blockStart = local.toLocalDate().atTime(local.hour / 6 * 6, 0)
            val t = byBlock.getOrPut(blockStart) { Totals() }
            val liquid = (h.rain.getOrNull(i) ?: 0.0) + (h.showers.getOrNull(i) ?: 0.0)
            if (isFreezing(h.weatherCode.getOrNull(i))) t.ice += liquid else t.rain += liquid
            t.snow += h.snowfall.getOrNull(i) ?: 0.0
        }
        return byBlock.flatMap { (start, t) ->
            val from = start.atZone(zone).toInstant()
            val to = start.plusHours(6).atZone(zone).toInstant()
            listOfNotNull(
                AmountBlock(from, to, PrecipType.RAIN, t.rain).takeIf { t.rain >= RAIN_FREEZING_THRESHOLD },
                AmountBlock(from, to, PrecipType.FREEZING_RAIN, t.ice).takeIf { t.ice >= RAIN_FREEZING_THRESHOLD },
                AmountBlock(from, to, PrecipType.SNOW, t.snow).takeIf { t.snow >= SNOW_THRESHOLD },
            )
        }
    }
}
