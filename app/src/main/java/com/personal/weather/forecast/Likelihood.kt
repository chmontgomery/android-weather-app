package com.personal.weather.forecast

import com.personal.weather.nws.WeatherCondition

enum class PrecipType { RAIN, SNOW, FREEZING_RAIN, SLEET }

/** Declaration order is the ranking (higher ordinal = more likely). Labels match forecast.weather.gov. */
enum class Likelihood(val axisLabel: String) {
    SLIGHT_CHANCE("SChc"),
    CHANCE("Chc"),
    LIKELY("Lkly"),
    OCCASIONAL("Ocnl"),
}

object LikelihoodMapper {
    fun typeOf(weather: String?): PrecipType? = when (weather) {
        "rain", "rain_showers", "drizzle", "thunderstorms" -> PrecipType.RAIN
        "snow", "snow_showers", "blowing_snow" -> PrecipType.SNOW
        "freezing_rain", "freezing_drizzle" -> PrecipType.FREEZING_RAIN
        "sleet", "ice_pellets" -> PrecipType.SLEET
        else -> null
    }

    fun likelihoodOf(coverage: String?): Likelihood = when (coverage) {
        "slight_chance", "isolated" -> Likelihood.SLIGHT_CHANCE
        "chance", "scattered", "patchy", "areas" -> Likelihood.CHANCE
        "likely", "numerous", "widespread" -> Likelihood.LIKELY
        "occasional", "periods", "frequent", "definite" -> Likelihood.OCCASIONAL
        else -> Likelihood.CHANCE
    }

    /** Precip types present in one hour's conditions; the highest likelihood wins per type. */
    fun map(conditions: List<WeatherCondition>): Map<PrecipType, Likelihood> {
        val out = mutableMapOf<PrecipType, Likelihood>()
        for (c in conditions) {
            val type = typeOf(c.weather) ?: continue
            val level = likelihoodOf(c.coverage)
            val existing = out[type]
            if (existing == null || level > existing) out[type] = level
        }
        return out
    }
}
