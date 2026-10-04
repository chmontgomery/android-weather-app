package com.personal.weather.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun distance_blaineToPineCityIsAbout75km() {
        assertEquals(75.0, Geo.distanceKm(45.16, -93.23, 45.83, -92.97), 5.0)
    }

    @Test fun isNear_withinTenKm() {
        val blaine = Place("Blaine, MN", 45.16, -93.23)
        assertTrue(Geo.isNear(blaine, 45.17, -93.20))
        assertFalse(Geo.isNear(blaine, 45.83, -92.97))
    }
}
