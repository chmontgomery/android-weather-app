package com.personal.weather.data

import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.forecast.TestSnapshots
import com.personal.weather.location.Country
import com.personal.weather.location.Place
import com.personal.weather.nws.Measurement
import com.personal.weather.nws.ObservationProperties
import com.personal.weather.openmeteo.OpenMeteoCurrent
import com.personal.weather.openmeteo.OpenMeteoHourly
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForecastCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private val file get() = File(tmp.root, "forecast.json")

    @Test fun missingFileLoadsNull() {
        assertNull(ForecastCache(file).load())
    }

    @Test fun roundTrip() {
        val snap = TestSnapshots.snapshot(observation = ObservationProperties("2026-10-03T18:45:00+00:00", Measurement(20.0)))
        ForecastCache(file).save(snap)
        assertEquals(snap, ForecastCache(file).load())
    }

    @Test fun saveOverwrites() {
        val cache = ForecastCache(file)
        cache.save(TestSnapshots.snapshot())
        val second = TestSnapshots.snapshot(fetchedAt = TestSnapshots.NOW.plusSeconds(60))
        cache.save(second)
        assertEquals(second, cache.load())
    }

    @Test fun corruptFileLoadsNull() {
        file.writeText("{not json")
        assertNull(ForecastCache(file).load())
    }

    @Test fun otherSchemaVersionLoadsNull() {
        ForecastCache(file).save(TestSnapshots.snapshot())
        file.writeText(file.readText().replace("\"version\":${ForecastCache.SCHEMA_VERSION}", "\"version\":999"))
        assertNull(ForecastCache(file).load())
    }

    @Test fun schemaV1FileIsIgnored() {
        // A version-1 file from the previous app version (flat NWS snapshot, no type discriminator).
        file.writeText("""{"version":1,"snapshot":{"place":{"name":"Blaine, MN","lat":45.16,"lon":-93.23},"fetchedAtEpochMs":1,"timeZone":"America/Chicago","grid":{}}}""")
        assertNull(ForecastCache(file).load())
    }

    @Test fun roundTripOpenMeteo() {
        val snap = ForecastSnapshot.OpenMeteo(
            place = Place("Lagos, Faro", 37.1, -8.67, Country.PORTUGAL),
            fetchedAtEpochMs = 1_791_000_000_000,
            timeZone = "Europe/Lisbon",
            hourly = OpenMeteoHourly(time = listOf(1_791_068_400), temperature = listOf(70.5), weatherCode = listOf(3)),
            current = OpenMeteoCurrent(1_791_068_400, 70.1),
        )
        ForecastCache(file).save(snap)
        assertEquals(snap, ForecastCache(file).load())
    }
}
