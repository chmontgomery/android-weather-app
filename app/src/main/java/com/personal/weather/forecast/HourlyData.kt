package com.personal.weather.forecast

import java.time.Instant

/** One hour's values in the app's units, whatever the source. */
data class HourValues(
    val tempF: Double?,
    val windChillF: Double?,
    val popPct: Int?,
    val skyPct: Int?,
    val likelihood: Map<PrecipType, Likelihood>,
    val windMph: Double?,
    val gustMph: Double?,
)

/** A source's forecast converted to hourly values; [ForecastBuilder] slices it into days. */
data class HourlyData(
    val hours: Map<Instant, HourValues>,
    val amounts: List<AmountBlock>,
    /** A current reading (station observation or model "current"), used if fresh. */
    val currentTempF: Double?,
    val currentTempAt: Instant?,
)
