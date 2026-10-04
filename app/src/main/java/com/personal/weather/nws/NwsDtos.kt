package com.personal.weather.nws

import kotlinx.serialization.Serializable

// Only the fields the app uses. Everything else in the NWS responses is ignored.

@Serializable
data class PointsResponse(val properties: PointsProperties)

@Serializable
data class PointsProperties(
    val forecastGridData: String,
    val observationStations: String,
    val timeZone: String,
    val relativeLocation: RelativeLocation,
)

@Serializable
data class RelativeLocation(val properties: RelativeLocationProperties)

@Serializable
data class RelativeLocationProperties(val city: String, val state: String)

@Serializable
data class GridResponse(val properties: GridProperties)

@Serializable
data class GridProperties(
    val temperature: NumericSeries = NumericSeries(),
    val windChill: NumericSeries = NumericSeries(),
    val probabilityOfPrecipitation: NumericSeries = NumericSeries(),
    val skyCover: NumericSeries = NumericSeries(),
    val windSpeed: NumericSeries = NumericSeries(),
    val windGust: NumericSeries = NumericSeries(),
    val quantitativePrecipitation: NumericSeries = NumericSeries(),
    val snowfallAmount: NumericSeries = NumericSeries(),
    val iceAccumulation: NumericSeries = NumericSeries(),
    val weather: WeatherSeries = WeatherSeries(),
)

@Serializable
data class NumericSeries(val uom: String? = null, val values: List<NumericValue> = emptyList())

/** [validTime] is "<ISO instant>/<ISO duration>", e.g. "2026-10-03T12:00:00+00:00/PT2H". */
@Serializable
data class NumericValue(val validTime: String, val value: Double? = null)

@Serializable
data class WeatherSeries(val values: List<WeatherValue> = emptyList())

@Serializable
data class WeatherValue(val validTime: String, val value: List<WeatherCondition> = emptyList())

@Serializable
data class WeatherCondition(
    val coverage: String? = null,
    val weather: String? = null,
    val intensity: String? = null,
)

@Serializable
data class StationsResponse(val features: List<StationFeature> = emptyList())

@Serializable
data class StationFeature(val properties: StationProperties)

@Serializable
data class StationProperties(val stationIdentifier: String)

@Serializable
data class ObservationResponse(val properties: ObservationProperties)

@Serializable
data class ObservationProperties(val timestamp: String, val temperature: Measurement = Measurement())

@Serializable
data class Measurement(val value: Double? = null)
