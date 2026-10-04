package com.personal.weather.data

import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.location.Country
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceNamer
import com.personal.weather.nws.NwsClient
import com.personal.weather.nws.NwsException
import com.personal.weather.openmeteo.OpenMeteoClient
import com.personal.weather.openmeteo.OpenMeteoException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherRepositoryTest {
    private lateinit var server: MockWebServer
    private val fetchedAt = Instant.parse("2026-10-03T19:00:00Z")
    private var gridResponse = MockResponse()
    private var stationsResponse = MockResponse()

    @Before fun setUp() {
        server = MockWebServer()
        gridResponse = MockResponse().setBody(
            """{"properties":{"temperature":{"uom":"wmoUnit:degC","values":[{"validTime":"2026-10-03T12:00:00+00:00/PT1H","value":12.5}]}}}"""
        )
        stationsResponse = MockResponse().setBody("""{"features":[{"properties":{"stationIdentifier":"KANE"}}]}""")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/points/") -> MockResponse().setBody("""
                        {"properties":{
                          "forecastGridData":"${server.url("/gridpoints/MPX/109,81")}",
                          "observationStations":"${server.url("/gridpoints/MPX/109,81/stations")}",
                          "timeZone":"America/Chicago",
                          "relativeLocation":{"properties":{"city":"Blaine","state":"MN"}}}}
                    """.trimIndent())
                    path == "/gridpoints/MPX/109,81" -> gridResponse
                    path == "/gridpoints/MPX/109,81/stations" -> stationsResponse
                    path == "/stations/KANE/observations/latest" -> MockResponse().setBody(
                        """{"properties":{"timestamp":"2026-10-03T18:45:00+00:00","temperature":{"value":20}}}"""
                    )
                    path.startsWith("/v1/forecast") -> openMeteoResponse
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private var nameLookup: suspend (Double, Double) -> String? = { _, _ -> "Lagos, Faro" }
    private val namer = object : PlaceNamer { override suspend fun nameFor(lat: Double, lon: Double) = nameLookup(lat, lon) }
    private var openMeteoResponse = MockResponse().setBody(javaClass.getResource("/openmeteo/lisbon.json")!!.readText())

    private fun repo() = WeatherRepository(
        NwsClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'), retryDelayMs = 0),
        OpenMeteoClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'), retryDelayMs = 0),
        namer,
    ) { fetchedAt }

    @Test fun fetch_currentLocationUsesNwsCityName() = runTest {
        val snap = repo().fetch(45.16, -93.23, null, Country.US) as ForecastSnapshot.Nws
        assertEquals("Blaine, MN", snap.place.name)
        assertEquals(45.16, snap.place.lat, 0.0)
        assertEquals(-93.23, snap.place.lon, 0.0)
        assertEquals("America/Chicago", snap.timeZone)
        assertEquals(fetchedAt, snap.fetchedAt)
        assertEquals(12.5, snap.grid.temperature.values.single().value!!, 0.0)
        assertEquals(20.0, snap.observation!!.temperature.value!!, 0.0)
    }

    @Test fun fetch_chosenPlaceKeepsItsName() = runTest {
        assertEquals("Pine City, MN", repo().fetch(45.83, -92.97, "Pine City, MN", Country.US).place.name)
    }

    @Test fun fetch_observationFailureIsNonFatal() = runTest {
        stationsResponse = MockResponse().setResponseCode(500)
        assertNull((repo().fetch(45.16, -93.23, null, Country.US) as ForecastSnapshot.Nws).observation)
    }

    @Test fun fetch_gridFailureThrows() = runTest {
        gridResponse = MockResponse().setResponseCode(500)
        val error = runCatching { repo().fetch(45.16, -93.23, null, Country.US) }.exceptionOrNull()
        assertTrue(error is NwsException.Http)
    }

    @Test fun fetch_emptyGridThrowsBadResponse() = runTest {
        gridResponse = MockResponse().setBody("""{"properties":{"temperature":{"uom":"wmoUnit:degC","values":[]}}}""")
        val error = runCatching { repo().fetch(45.16, -93.23, null, Country.US) }.exceptionOrNull()
        assertTrue(error is NwsException.BadResponse)
    }

    @Test fun portugal_usesOpenMeteoAndNamesTheLocation() = runTest {
        val snap = repo().fetch(37.10, -8.67, null, Country.PORTUGAL) as ForecastSnapshot.OpenMeteo
        assertEquals(Place("Lagos, Faro", 37.10, -8.67, Country.PORTUGAL), snap.place)
        assertEquals("Europe/Lisbon", snap.timeZone)
        assertEquals(fetchedAt, snap.fetchedAt)
        assertEquals(192, snap.hourly.time.size)
        assertTrue(server.takeRequest().path!!.startsWith("/v1/forecast"))
    }

    @Test fun portugal_chosenNameIsKeptWithoutLookup() = runTest {
        nameLookup = { _, _ -> error("should not be called") }
        assertEquals("Lisboa", repo().fetch(38.72, -9.14, "Lisboa", Country.PORTUGAL).place.name)
    }

    @Test fun portugal_nameLookupFailure_fallsBackToCurrentLocation() = runTest {
        nameLookup = { _, _ -> throw java.io.IOException("offline") }
        assertEquals(WeatherRepository.UNNAMED, repo().fetch(38.72, -9.14, null, Country.PORTUGAL).place.name)
    }

    @Test fun portugal_emptyForecastIsBadResponse() = runTest {
        openMeteoResponse = MockResponse().setBody("""{"timezone":"Europe/Lisbon","hourly":{"time":[1791068400],"temperature_2m":[null]}}""")
        val e = runCatching { repo().fetch(38.72, -9.14, "Lisboa", Country.PORTUGAL) }.exceptionOrNull()
        assertTrue(e is OpenMeteoException)
    }
}
