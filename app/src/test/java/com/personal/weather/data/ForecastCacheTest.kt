package com.personal.weather.data

import com.personal.weather.forecast.TestSnapshots
import com.personal.weather.nws.Measurement
import com.personal.weather.nws.ObservationProperties
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
}
