package com.personal.weather.forecast

import com.personal.weather.nws.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LikelihoodMapperTest {
    @Test fun typeOf_mapsNwsWeatherToFourTypes() {
        listOf("rain", "rain_showers", "drizzle", "thunderstorms").forEach { assertEquals(PrecipType.RAIN, LikelihoodMapper.typeOf(it)) }
        listOf("snow", "snow_showers", "blowing_snow").forEach { assertEquals(PrecipType.SNOW, LikelihoodMapper.typeOf(it)) }
        listOf("freezing_rain", "freezing_drizzle").forEach { assertEquals(PrecipType.FREEZING_RAIN, LikelihoodMapper.typeOf(it)) }
        listOf("sleet", "ice_pellets").forEach { assertEquals(PrecipType.SLEET, LikelihoodMapper.typeOf(it)) }
        listOf("fog", "haze", "smoke", null).forEach { assertNull(LikelihoodMapper.typeOf(it)) }
    }

    @Test fun likelihoodOf_mapsCoverage() {
        assertEquals(Likelihood.SLIGHT_CHANCE, LikelihoodMapper.likelihoodOf("slight_chance"))
        assertEquals(Likelihood.SLIGHT_CHANCE, LikelihoodMapper.likelihoodOf("isolated"))
        assertEquals(Likelihood.CHANCE, LikelihoodMapper.likelihoodOf("chance"))
        assertEquals(Likelihood.CHANCE, LikelihoodMapper.likelihoodOf("scattered"))
        assertEquals(Likelihood.LIKELY, LikelihoodMapper.likelihoodOf("likely"))
        assertEquals(Likelihood.LIKELY, LikelihoodMapper.likelihoodOf("numerous"))
        assertEquals(Likelihood.OCCASIONAL, LikelihoodMapper.likelihoodOf("occasional"))
        assertEquals(Likelihood.OCCASIONAL, LikelihoodMapper.likelihoodOf("definite"))
        assertEquals(Likelihood.CHANCE, LikelihoodMapper.likelihoodOf("something_new"))
        assertEquals(Likelihood.CHANCE, LikelihoodMapper.likelihoodOf(null))
    }

    @Test fun map_highestLikelihoodWinsPerType() {
        val out = LikelihoodMapper.map(listOf(
            WeatherCondition("chance", "rain_showers"),
            WeatherCondition("likely", "thunderstorms"),
            WeatherCondition("slight_chance", "snow"),
        ))
        assertEquals(mapOf(PrecipType.RAIN to Likelihood.LIKELY, PrecipType.SNOW to Likelihood.SLIGHT_CHANCE), out)
    }

    @Test fun map_noWeatherEntryIsEmpty() {
        // NWS's common "nothing happening" entry: all fields null.
        assertTrue(LikelihoodMapper.map(listOf(WeatherCondition(null, null, null))).isEmpty())
    }

    @Test fun likelihoodOrderingIsRanking() {
        assertTrue(Likelihood.OCCASIONAL > Likelihood.LIKELY)
        assertTrue(Likelihood.LIKELY > Likelihood.CHANCE)
        assertTrue(Likelihood.CHANCE > Likelihood.SLIGHT_CHANCE)
    }
}
