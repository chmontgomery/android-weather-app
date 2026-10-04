package com.personal.weather.nws

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NwsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NwsClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = NwsClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'), retryDelayMs = 0)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun pointsJson() = """
        {"properties":{
          "forecastGridData":"${server.url("/gridpoints/MPX/109,81")}",
          "observationStations":"${server.url("/gridpoints/MPX/109,81/stations")}",
          "timeZone":"America/Chicago",
          "relativeLocation":{"type":"Feature","properties":{"city":"Blaine","state":"MN"}}
        }}
    """.trimIndent()

    @Test fun points_parsesAndSendsRequiredHeaders() = runTest {
        server.enqueue(MockResponse().setBody(pointsJson()))

        val p = client.points(45.16, -93.23)

        assertEquals("America/Chicago", p.timeZone)
        assertEquals("Blaine", p.relativeLocation.properties.city)
        assertEquals("MN", p.relativeLocation.properties.state)
        val req = server.takeRequest()
        assertEquals("/points/45.16,-93.23", req.path)
        assertEquals(NwsClient.USER_AGENT, req.getHeader("User-Agent"))
        assertEquals("application/geo+json", req.getHeader("Accept"))
    }

    @Test fun formatCoord_roundsToFourDecimalsAndStripsZeros() {
        assertEquals("45.1235", NwsClient.formatCoord(45.123456))
        assertEquals("-93", NwsClient.formatCoord(-93.000049))
        assertEquals("-93.2", NwsClient.formatCoord(-93.2))
    }

    @Test fun points_404MapsToOutsideCoverageWithoutRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"title":"Data Unavailable For Requested Point"}"""))

        val error = runCatching { client.points(51.5, -0.12) }.exceptionOrNull()

        assertTrue(error is NwsException.OutsideCoverage)
        assertEquals(1, server.requestCount)
    }

    @Test fun serverErrorIsRetriedOnce() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(pointsJson()))

        val p = client.points(45.16, -93.23)

        assertEquals("America/Chicago", p.timeZone)
        assertEquals(2, server.requestCount)
    }

    @Test fun serverErrorTwiceThrowsHttp() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { client.points(45.16, -93.23) }.exceptionOrNull()

        assertTrue(error is NwsException.Http && error.code == 503)
        assertEquals(2, server.requestCount)
    }

    @Test fun gridpoints_parsesSeriesAndWeather() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"properties":{
              "updateTime":"2026-10-03T18:26:18+00:00",
              "temperature":{"uom":"wmoUnit:degC","values":[{"validTime":"2026-10-03T12:00:00+00:00/PT2H","value":12.5}]},
              "windChill":{"uom":"wmoUnit:degC","values":[{"validTime":"2026-10-03T12:00:00+00:00/PT21H","value":null}]},
              "weather":{"values":[{"validTime":"2026-10-03T13:00:00+00:00/PT3H","value":[
                {"coverage":"chance","weather":"rain_showers","intensity":"light","visibility":{"unitCode":"wmoUnit:km","value":null},"attributes":[]}
              ]}]}
            }}
        """.trimIndent()))

        val g = client.gridpoints(server.url("/gridpoints/MPX/109,81").toString())

        assertEquals("wmoUnit:degC", g.temperature.uom)
        assertEquals(12.5, g.temperature.values.single().value!!, 0.0)
        assertNull(g.windChill.values.single().value)
        val cond = g.weather.values.single().value.single()
        assertEquals("chance", cond.coverage)
        assertEquals("rain_showers", cond.weather)
    }

    @Test fun gridpoints_missingSeriesDefaultToEmpty() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"properties":{"temperature":{"uom":"wmoUnit:degC","values":[]}}}
        """.trimIndent()))

        val g = client.gridpoints(server.url("/gridpoints/MPX/109,81").toString())

        assertTrue(g.windChill.values.isEmpty())
        assertTrue(g.probabilityOfPrecipitation.values.isEmpty())
        assertTrue(g.skyCover.values.isEmpty())
        assertTrue(g.weather.values.isEmpty())
        assertTrue(g.snowfallAmount.values.isEmpty())
    }

    @Test fun latestObservation_usesFirstStation() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"features":[{"properties":{"stationIdentifier":"KANE"}},{"properties":{"stationIdentifier":"KMSP"}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"properties":{"timestamp":"2026-10-03T18:45:00+00:00","temperature":{"unitCode":"wmoUnit:degC","value":20,"qualityControl":"V"}}}
        """.trimIndent()))

        val obs = client.latestObservation(server.url("/gridpoints/MPX/109,81/stations").toString())

        assertEquals(20.0, obs!!.temperature.value!!, 0.0)
        assertEquals("2026-10-03T18:45:00+00:00", obs.timestamp)
        server.takeRequest()
        assertEquals("/stations/KANE/observations/latest", server.takeRequest().path)
    }

    @Test fun latestObservation_nullTemperatureValueParses() = runTest {
        server.enqueue(MockResponse().setBody("""{"features":[{"properties":{"stationIdentifier":"KANE"}}]}"""))
        server.enqueue(MockResponse().setBody("""
            {"properties":{"timestamp":"2026-10-03T18:45:00+00:00","temperature":{"unitCode":"wmoUnit:degC","value":null}}}
        """.trimIndent()))

        val obs = client.latestObservation(server.url("/gridpoints/MPX/109,81/stations").toString())

        assertNull(obs!!.temperature.value)
    }

    @Test fun latestObservation_failureReturnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(client.latestObservation(server.url("/gridpoints/MPX/109,81/stations").toString()))
    }
}
