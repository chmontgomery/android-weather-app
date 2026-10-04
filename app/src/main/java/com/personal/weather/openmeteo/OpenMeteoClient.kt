package com.personal.weather.openmeteo

import com.personal.weather.AppJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenMeteoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Open-Meteo forecast API (free, keyless; CC BY 4.0 — credited in the app). Retries once on 5xx / network failure. */
class OpenMeteoClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com",
    private val retryDelayMs: Long = 1_000,
) {
    suspend fun forecast(lat: Double, lon: Double): OpenMeteoResponse {
        val url = "${baseUrl.trimEnd('/')}/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("hourly", HOURLY)
            .addQueryParameter("current", "temperature_2m")
            .addQueryParameter("temperature_unit", "fahrenheit")
            .addQueryParameter("wind_speed_unit", "mph")
            .addQueryParameter("precipitation_unit", "inch")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_days", "8")
            .build()
        val body = try {
            getOnce(url)
        } catch (e: Retryable) {
            delay(retryDelayMs)
            try { getOnce(url) } catch (e2: Retryable) { throw OpenMeteoException(e2.message ?: "Open-Meteo failed", e2.cause) }
        }
        return try {
            AppJson.decodeFromString(OpenMeteoResponse.serializer(), body)
        } catch (e: IllegalArgumentException) {
            throw OpenMeteoException("Unreadable response", e)
        }
    }

    /** Network failure or 5xx: worth one retry. */
    private class Retryable(message: String, cause: Throwable? = null) : Exception(message, cause)

    private suspend fun getOnce(url: okhttp3.HttpUrl): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> text
                    response.code >= 500 -> throw Retryable("HTTP ${response.code}")
                    else -> throw OpenMeteoException("HTTP ${response.code}: $text")
                }
            }
        } catch (e: IOException) {
            throw Retryable("Network error: ${e.message}", e)
        }
    }

    private companion object {
        const val HOURLY =
            "temperature_2m,precipitation_probability,cloud_cover,wind_speed_10m,wind_gusts_10m,rain,showers,snowfall,weather_code"
    }
}
