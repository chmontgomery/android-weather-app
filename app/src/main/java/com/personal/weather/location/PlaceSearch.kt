package com.personal.weather.location

import com.personal.weather.AppJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** One search hit as shown in the list; [key] is the geocoder's handle for looking up its coordinates. */
data class PlaceSuggestion(val label: String, val key: String)

interface PlaceSearch {
    /** US cities and ZIPs matching what's typed so far. Throws [PlaceSearchUnavailable] on failure. */
    suspend fun search(query: String): List<PlaceSuggestion>

    /** Coordinates for a picked suggestion. Throws [PlaceSearchUnavailable] on failure. */
    suspend fun resolve(suggestion: PlaceSuggestion): Place
}

class PlaceSearchUnavailable(message: String = "Place search unavailable", cause: Throwable? = null) : Exception(message, cause)

/**
 * Esri's ArcGIS World Geocoder — the same service behind forecast.weather.gov's city/ZIP box, so the
 * suggestion list matches it. Keyless: "suggest" gives labels as you type, "findAddressCandidates"
 * turns the picked one into coordinates.
 */
class ArcGisPlaceSearch(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer",
) : PlaceSearch {
    override suspend fun search(query: String): List<PlaceSuggestion> {
        val url = endpoint("suggest")
            .addQueryParameter("text", query)
            .addQueryParameter("countryCode", "USA")
            .addQueryParameter("category", "City,Postal")
            .addQueryParameter("maxSuggestions", MAX_RESULTS.toString())
            .addQueryParameter("f", "json")
            .build()
        val response = get<SuggestResponse>(url)
        return response.suggestions
            .filterNot { it.isCollection }
            .map { PlaceSuggestion(it.text.removeSuffix(", USA").replace(", USA (", " ("), it.magicKey) }
            .distinctBy { it.label } // labels are list keys in the UI; the duplicates are the same town
    }

    override suspend fun resolve(suggestion: PlaceSuggestion): Place {
        val url = endpoint("findAddressCandidates")
            .addQueryParameter("SingleLine", suggestion.label)
            .addQueryParameter("magicKey", suggestion.key)
            .addQueryParameter("maxLocations", "1")
            .addQueryParameter("f", "json")
            .build()
        val location = get<CandidatesResponse>(url).candidates.firstOrNull()?.location
            ?: throw PlaceSearchUnavailable("No coordinates for ${suggestion.label}")
        return Place(suggestion.label, location.y, location.x)
    }

    private fun endpoint(operation: String): HttpUrl.Builder =
        "${baseUrl.trimEnd('/')}/$operation".toHttpUrl().newBuilder()

    private suspend inline fun <reified T : ArcGisResponse> get(url: HttpUrl): T = withContext(Dispatchers.IO) {
        val body = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw PlaceSearchUnavailable("HTTP ${response.code}")
                response.body?.string() ?: throw PlaceSearchUnavailable("Empty response")
            }
        } catch (e: IOException) {
            throw PlaceSearchUnavailable("Network error", e)
        }
        val parsed = try {
            AppJson.decodeFromString(serializer<T>(), body)
        } catch (e: IllegalArgumentException) {
            throw PlaceSearchUnavailable("Unreadable response", e)
        }
        // ArcGIS reports failures as HTTP 200 with an "error" object.
        parsed.error?.let { throw PlaceSearchUnavailable("ArcGIS error ${it.code}: ${it.message}") }
        parsed
    }

    companion object {
        const val MAX_RESULTS = 10
    }
}

private interface ArcGisResponse {
    val error: ArcGisError?
}

@Serializable
private data class ArcGisError(val code: Int? = null, val message: String? = null)

@Serializable
private data class SuggestResponse(
    val suggestions: List<Suggestion> = emptyList(),
    override val error: ArcGisError? = null,
) : ArcGisResponse

@Serializable
private data class Suggestion(val text: String, val magicKey: String, val isCollection: Boolean = false)

@Serializable
private data class CandidatesResponse(
    val candidates: List<Candidate> = emptyList(),
    override val error: ArcGisError? = null,
) : ArcGisResponse

@Serializable
private data class Candidate(val location: Point)

@Serializable
private data class Point(val x: Double, val y: Double)
