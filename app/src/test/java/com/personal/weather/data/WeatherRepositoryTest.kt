package com.personal.weather.data

import com.personal.weather.nws.NwsClient
import com.personal.weather.nws.NwsException
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
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun repo() = WeatherRepository(
        NwsClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'), retryDelayMs = 0),
    ) { fetchedAt }

    @Test fun fetch_currentLocationUsesNwsCityName() = runTest {
        val snap = repo().fetch(45.16, -93.23, null)
        assertEquals("Blaine, MN", snap.place.name)
        assertEquals(45.16, snap.place.lat, 0.0)
        assertEquals(-93.23, snap.place.lon, 0.0)
        assertEquals("America/Chicago", snap.timeZone)
        assertEquals(fetchedAt, snap.fetchedAt)
        assertEquals(12.5, snap.grid.temperature.values.single().value!!, 0.0)
        assertEquals(20.0, snap.observation!!.temperature.value!!, 0.0)
    }

    @Test fun fetch_chosenPlaceKeepsItsName() = runTest {
        assertEquals("Pine City, MN", repo().fetch(45.83, -92.97, "Pine City, MN").place.name)
    }

    @Test fun fetch_observationFailureIsNonFatal() = runTest {
        stationsResponse = MockResponse().setResponseCode(500)
        assertNull(repo().fetch(45.16, -93.23, null).observation)
    }

    @Test fun fetch_gridFailureThrows() = runTest {
        gridResponse = MockResponse().setResponseCode(500)
        val error = runCatching { repo().fetch(45.16, -93.23, null) }.exceptionOrNull()
        assertTrue(error is NwsException.Http)
    }

    @Test fun fetch_emptyGridThrowsBadResponse() = runTest {
        gridResponse = MockResponse().setBody("""{"properties":{"temperature":{"uom":"wmoUnit:degC","values":[]}}}""")
        val error = runCatching { repo().fetch(45.16, -93.23, null) }.exceptionOrNull()
        assertTrue(error is NwsException.BadResponse)
    }
}
