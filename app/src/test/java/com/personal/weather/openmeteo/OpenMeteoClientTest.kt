package com.personal.weather.openmeteo

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenMeteoClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoClient
    private val lisbon = javaClass.getResource("/openmeteo/lisbon.json")!!.readText()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenMeteoClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'), retryDelayMs = 0)
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun forecast_parsesRecordedLisbonResponse() = runTest {
        server.enqueue(MockResponse().setBody(lisbon))
        val r = client.forecast(38.72, -9.14)
        assertEquals("Europe/Lisbon", r.timezone)
        assertEquals(192, r.hourly.time.size)
        assertEquals(192, r.hourly.temperature.size)
        assertEquals(192, r.hourly.weatherCode.size)
        assertTrue(r.current!!.temperature != null)
    }

    @Test fun forecast_sendsRequiredParameters() = runTest {
        server.enqueue(MockResponse().setBody(lisbon))
        client.forecast(38.72, -9.14)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals("38.72", url.queryParameter("latitude"))
        assertEquals("-9.14", url.queryParameter("longitude"))
        assertEquals(
            "temperature_2m,precipitation_probability,cloud_cover,wind_speed_10m,wind_gusts_10m,rain,showers,snowfall,weather_code",
            url.queryParameter("hourly"),
        )
        assertEquals("temperature_2m", url.queryParameter("current"))
        assertEquals("fahrenheit", url.queryParameter("temperature_unit"))
        assertEquals("mph", url.queryParameter("wind_speed_unit"))
        assertEquals("inch", url.queryParameter("precipitation_unit"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals("8", url.queryParameter("forecast_days"))
    }

    @Test fun forecast_retriesOnceOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(lisbon))
        assertEquals("Europe/Lisbon", client.forecast(38.72, -9.14).timezone)
        assertEquals(2, server.requestCount)
    }

    @Test fun forecast_errorBodyFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":true,"reason":"Latitude must be in range"}"""))
        val e = runCatching { client.forecast(99.0, 0.0) }.exceptionOrNull()
        assertTrue(e is OpenMeteoException)
        assertEquals(1, server.requestCount)
    }

    @Test fun forecast_unreadableBodyFails() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(runCatching { client.forecast(38.72, -9.14) }.exceptionOrNull() is OpenMeteoException)
    }
}
