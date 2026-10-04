package com.personal.weather.location

import com.personal.weather.AppJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** One search hit as shown in the list; [key] is the geocoder's handle for looking up its coordinates. */
data class PlaceSuggestion(val label: String, val key: String)

interface PlaceSearch {
    /** US and Portuguese cities and postcodes matching what's typed so far. Throws [PlaceSearchUnavailable] on failure. */
    suspend fun search(query: String): List<PlaceSuggestion>

    /** Coordinates for a picked suggestion. Throws [PlaceSearchUnavailable] on failure. */
    suspend fun resolve(suggestion: PlaceSuggestion): Place
}

/** Names a coordinate ("Lagos, Faro") for sources that don't supply place names. */
interface PlaceNamer {
    /** Null when no name is available. May throw on network failure. */
    suspend fun nameFor(lat: Double, lon: Double): String?
}

class PlaceSearchUnavailable(message: String = "Place search unavailable", cause: Throwable? = null) : Exception(message, cause)

/**
 * Esri's ArcGIS World Geocoder — the same service behind forecast.weather.gov's city/ZIP box, so the
 * suggestion list matches it. Keyless: "suggest" gives labels as you type, "findAddressCandidates"
 * turns the picked one into coordinates. Limited to the US (incl. Puerto Rico) and Portugal.
 */
class ArcGisPlaceSearch(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer",
) : PlaceSearch, PlaceNamer {
    override suspend fun search(query: String): List<PlaceSuggestion> {
        val url = endpoint("suggest")
            .addQueryParameter("text", query)
            .addQueryParameter("countryCode", "USA,PRT")
            .addQueryParameter("category", "City,Postal")
            .addQueryParameter("maxSuggestions", MAX_RESULTS.toString())
            .addQueryParameter("f", "json")
            .build()
        val response = get<SuggestResponse>(url)
        return response.suggestions
            .filterNot { it.isCollection }
            .map { PlaceSuggestion(labelOf(it.text), it.magicKey) }
            .distinctBy { it.label } // labels are list keys in the UI; the duplicates are the same town
    }

    override suspend fun resolve(suggestion: PlaceSuggestion): Place {
        val url = endpoint("findAddressCandidates")
            .addQueryParameter("SingleLine", suggestion.label)
            .addQueryParameter("magicKey", suggestion.key)
            .addQueryParameter("maxLocations", "1")
            .addQueryParameter("outFields", "Country")
            .addQueryParameter("f", "json")
            .build()
        val candidate = get<CandidatesResponse>(url).candidates.firstOrNull()
            ?: throw PlaceSearchUnavailable("No coordinates for ${suggestion.label}")
        val country = when (candidate.attributes.country) {
            "PRT" -> Country.PORTUGAL
            else -> Country.US // incl. territories (PRI, VIR, GUM...); weather.gov decides coverage
        }
        return Place(suggestion.label, candidate.location.y, candidate.location.x, country)
    }

    override suspend fun nameFor(lat: Double, lon: Double): String? {
        val url = endpoint("reverseGeocode")
            .addQueryParameter("location", "$lon,$lat")
            .addQueryParameter("featureTypes", "Locality")
            .addQueryParameter("langCode", "PT")
            .addQueryParameter("f", "json")
            .build()
        val address = get<ReverseResponse>(url).address ?: return null
        val city = address.city?.takeIf { it.isNotBlank() } ?: return null
        val region = address.region?.takeIf { it.isNotBlank() && it != city }
        return if (region != null) "$city, $region" else city
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

        /** ArcGIS suggestion text → list label: drop ", USA" / ", PRT"; Puerto Rico's ", PRI" becomes ", PR". */
        fun labelOf(text: String): String = text
            .replace(", USA (", " (").removeSuffix(", USA")
            .replace(", PRT (", " (").removeSuffix(", PRT")
            .replace(", PRI (", ", PR (").let { if (it.endsWith(", PRI")) it.removeSuffix(", PRI") + ", PR" else it }
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
private data class Candidate(val location: Point, val attributes: CandidateAttributes = CandidateAttributes())

@Serializable
private data class CandidateAttributes(@SerialName("Country") val country: String? = null)

@Serializable
private data class ReverseResponse(
    val address: ReverseAddress? = null,
    override val error: ArcGisError? = null,
) : ArcGisResponse

@Serializable
private data class ReverseAddress(@SerialName("City") val city: String? = null, @SerialName("Region") val region: String? = null)

@Serializable
private data class Point(val x: Double, val y: Double)
