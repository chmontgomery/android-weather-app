package com.personal.weather.location

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArcGisPlaceSearchTest {
    private lateinit var server: MockWebServer
    private lateinit var search: ArcGisPlaceSearch

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        search = ArcGisPlaceSearch(OkHttpClient(), baseUrl = server.url("/geocode").toString())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun search_returnsUsCityAndZipSuggestionsWithoutCountrySuffix() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"suggestions":[
              {"text":"Pine City, MN, USA","magicKey":"k1","isCollection":false},
              {"text":"Pine City, AR, USA","magicKey":"k2","isCollection":false},
              {"text":"Pine City, MN, USA (Pine County)","magicKey":"k3","isCollection":false},
              {"text":"Pine City, MN, USA","magicKey":"k4","isCollection":false},
              {"text":"Pine City Cafes","magicKey":"k5","isCollection":true}
            ]}
        """.trimIndent()))

        val results = search.search("pine city")

        assertEquals(
            listOf(
                PlaceSuggestion("Pine City, MN", "k1"),
                PlaceSuggestion("Pine City, AR", "k2"),
                PlaceSuggestion("Pine City, MN (Pine County)", "k3"),
            ),
            results,
        )
        val req = server.takeRequest().requestUrl!!
        assertEquals("/geocode/suggest", req.encodedPath)
        assertEquals("pine city", req.queryParameter("text"))
        assertEquals("USA,PRT", req.queryParameter("countryCode"))
        assertEquals("City,Postal", req.queryParameter("category"))
        assertEquals("10", req.queryParameter("maxSuggestions"))
        assertEquals("json", req.queryParameter("f"))
    }

    @Test fun search_zipWithLeadingZeroKeepsZero() = runTest {
        server.enqueue(MockResponse().setBody("""{"suggestions":[{"text":"02134, Boston, MA, USA","magicKey":"z1"}]}"""))

        assertEquals(listOf(PlaceSuggestion("02134, Boston, MA", "z1")), search.search("02134"))
    }

    @Test fun search_noMatchesIsEmpty() = runTest {
        server.enqueue(MockResponse().setBody("""{"suggestions":[]}"""))

        assertTrue(search.search("zzzz").isEmpty())
    }

    @Test fun search_httpErrorIsUnavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue(runCatching { search.search("pine") }.exceptionOrNull() is PlaceSearchUnavailable)
    }

    @Test fun search_errorBodyIsUnavailable() = runTest {
        // ArcGIS reports failures as HTTP 200 with an "error" object.
        server.enqueue(MockResponse().setBody("""{"error":{"code":498,"message":"Invalid token."}}"""))

        assertTrue(runCatching { search.search("pine") }.exceptionOrNull() is PlaceSearchUnavailable)
    }

    @Test fun resolve_looksUpCoordinatesByMagicKey() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"spatialReference":{"wkid":4326},"candidates":[
              {"address":"Pine City, Minnesota","location":{"x":-92.968837,"y":45.826625},"score":100,"attributes":{"Country":"USA"}}
            ]}
        """.trimIndent()))

        val place = search.resolve(PlaceSuggestion("Pine City, MN", "k1"))

        assertEquals(Place("Pine City, MN", 45.826625, -92.968837, Country.US), place)
        val req = server.takeRequest().requestUrl!!
        assertEquals("/geocode/findAddressCandidates", req.encodedPath)
        assertEquals("k1", req.queryParameter("magicKey"))
        assertEquals("Pine City, MN", req.queryParameter("SingleLine"))
        assertEquals("1", req.queryParameter("maxLocations"))
        assertEquals("Country", req.queryParameter("outFields"))
    }

    @Test fun resolve_noCandidateIsUnavailable() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))

        assertTrue(runCatching { search.resolve(PlaceSuggestion("Nowhere", "k")) }.exceptionOrNull() is PlaceSearchUnavailable)
    }

    @Test fun resolve_httpErrorIsUnavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(runCatching { search.resolve(PlaceSuggestion("Pine City, MN", "k1")) }.exceptionOrNull() is PlaceSearchUnavailable)
    }

    @Test fun search_portugueseLabelsDropCountryCode() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"suggestions":[
              {"text":"Lagos, Faro, PRT","magicKey":"p1"},
              {"text":"8600, Bensafrim, Lagos, Faro, PRT","magicKey":"p2"},
              {"text":"Lagos del Sol, AL, USA","magicKey":"u1"}
            ]}
        """.trimIndent()))
        assertEquals(
            listOf(PlaceSuggestion("Lagos, Faro", "p1"), PlaceSuggestion("8600, Bensafrim, Lagos, Faro", "p2"), PlaceSuggestion("Lagos del Sol, AL", "u1")),
            search.search("lagos"),
        )
    }

    @Test fun search_puertoRicoLabelledPR() = runTest {
        server.enqueue(MockResponse().setBody("""{"suggestions":[{"text":"Lagos de Plata, Toa Baja, PRI","magicKey":"r1"}]}"""))
        assertEquals(listOf(PlaceSuggestion("Lagos de Plata, Toa Baja, PR", "r1")), search.search("lagos"))
    }

    @Test fun resolve_mapsCountryCodes() = runTest {
        fun candidate(code: String) = MockResponse().setBody(
            """{"candidates":[{"location":{"x":-8.7,"y":37.1},"attributes":{"Country":"$code"}}]}"""
        )
        server.enqueue(candidate("PRT"))
        assertEquals(Country.PORTUGAL, search.resolve(PlaceSuggestion("Lagos, Faro", "p1")).country)
        server.enqueue(candidate("PRI"))
        assertEquals(Country.US, search.resolve(PlaceSuggestion("Lagos de Plata, Toa Baja, PR", "r1")).country)
        server.enqueue(candidate("GUM"))
        assertEquals(Country.US, search.resolve(PlaceSuggestion("Hagatna, GU", "g1")).country)
        server.enqueue(MockResponse().setBody("""{"candidates":[{"location":{"x":-64.9,"y":18.3},"attributes":{}}]}"""))
        assertEquals(Country.US, search.resolve(PlaceSuggestion("Charlotte Amalie, VI", "v1")).country)
    }

    @Test fun nameFor_cityAndRegion() = runTest {
        server.enqueue(MockResponse().setBody("""{"address":{"City":"Lagos","Region":"Faro","Match_addr":"Lagos, Faro"},"location":{"x":-8.67,"y":37.1}}"""))
        assertEquals("Lagos, Faro", search.nameFor(37.10, -8.67))
        val req = server.takeRequest().requestUrl!!
        assertEquals("/geocode/reverseGeocode", req.encodedPath)
        assertEquals("-8.67,37.1", req.queryParameter("location"))
        assertEquals("Locality", req.queryParameter("featureTypes"))
    }

    @Test fun nameFor_sameCityAndRegionShownOnce() = runTest {
        server.enqueue(MockResponse().setBody("""{"address":{"City":"Faro","Region":"Faro"}}"""))
        assertEquals("Faro", search.nameFor(37.02, -7.93))
    }

    @Test fun nameFor_noCityIsNull() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":{"code":400,"message":"Cannot perform query."}}"""))
        assertEquals(null, runCatching { search.nameFor(0.0, 0.0) }.getOrNull())
    }
}
