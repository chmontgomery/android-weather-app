package com.personal.weather.nws

import com.personal.weather.AppJson
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class NwsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class OutsideCoverage : NwsException("weather.gov has no data for this point")
    class Http(val code: Int) : NwsException("HTTP $code")
    class Network(cause: Throwable) : NwsException("Network error: ${cause.message}", cause)
    class BadResponse(cause: Throwable) : NwsException("Unreadable response", cause)
}

/** Thin client for api.weather.gov. Retries once on 5xx / network failure. */
class NwsClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.weather.gov",
    private val retryDelayMs: Long = 1_000,
) {
    suspend fun points(lat: Double, lon: Double): PointsProperties {
        val url = "$baseUrl/points/${formatCoord(lat)},${formatCoord(lon)}"
        val body = try {
            get(url)
        } catch (e: NwsException.Http) {
            if (e.code == 404) throw NwsException.OutsideCoverage() else throw e
        }
        return decode<PointsResponse>(body).properties
    }

    suspend fun gridpoints(url: String): GridProperties = decode<GridResponse>(get(url)).properties

    /** Latest observation from the nearest (first-listed) station; null on any failure so callers fall back to the forecast. */
    suspend fun latestObservation(stationsUrl: String): ObservationProperties? {
        return try {
            val station = decode<StationsResponse>(get(stationsUrl))
                .features.firstOrNull()?.properties?.stationIdentifier ?: return null
            decode<ObservationResponse>(get("$baseUrl/stations/$station/observations/latest")).properties
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun get(url: String): String {
        try {
            return getOnce(url)
        } catch (e: NwsException) {
            val retryable = e is NwsException.Network || (e is NwsException.Http && e.code >= 500)
            if (!retryable) throw e
        }
        delay(retryDelayMs)
        return getOnce(url)
    }

    private suspend fun getOnce(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/geo+json")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw NwsException.Http(response.code)
                response.body?.string() ?: throw NwsException.Http(response.code)
            }
        } catch (e: IOException) {
            throw NwsException.Network(e)
        }
    }

    private inline fun <reified T> decode(body: String): T = try {
        AppJson.decodeFromString(serializer<T>(), body)
    } catch (e: IllegalArgumentException) { // SerializationException extends IllegalArgumentException
        throw NwsException.BadResponse(e)
    }

    companion object {
        const val USER_AGENT = "(com.personal.weather, personal use)"

        /** NWS wants at most 4 decimals; extra precision gets a redirect. */
        fun formatCoord(v: Double): String =
            BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
