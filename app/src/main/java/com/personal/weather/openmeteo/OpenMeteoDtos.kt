package com.personal.weather.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields the app requests. Hourly arrays are index-aligned with [OpenMeteoHourly.time]; entries may be null.

@Serializable
data class OpenMeteoResponse(
    val timezone: String,
    val hourly: OpenMeteoHourly = OpenMeteoHourly(),
    val current: OpenMeteoCurrent? = null,
)

@Serializable
data class OpenMeteoHourly(
    /** Hour starts as Unix seconds (requested with timeformat=unixtime). */
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    val rain: List<Double?> = emptyList(),
    val showers: List<Double?> = emptyList(),
    val snowfall: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
data class OpenMeteoCurrent(val time: Long, @SerialName("temperature_2m") val temperature: Double? = null)
