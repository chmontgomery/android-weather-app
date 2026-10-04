package com.personal.weather.location

import com.personal.weather.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryTest {
    @Test fun of_portugalMainlandMadeiraAzores() {
        assertEquals(Country.PORTUGAL, Country.of(38.72, -9.14))   // Lisbon
        assertEquals(Country.PORTUGAL, Country.of(37.02, -7.93))   // Faro
        assertEquals(Country.PORTUGAL, Country.of(37.10, -8.67))   // Lagos
        assertEquals(Country.PORTUGAL, Country.of(32.65, -16.91))  // Funchal, Madeira
        assertEquals(Country.PORTUGAL, Country.of(37.74, -25.67))  // Ponta Delgada, Azores
    }

    @Test fun of_everywhereElseIsUs() {
        assertEquals(Country.US, Country.of(45.16, -93.23))  // Blaine
        assertEquals(Country.US, Country.of(40.42, -3.70))   // Madrid
        assertEquals(Country.US, Country.of(37.39, -5.98))   // Seville
    }

    @Test fun place_decodesWithoutCountryAsUs() {
        val place = AppJson.decodeFromString(Place.serializer(), """{"name":"Blaine, MN","lat":45.16,"lon":-93.23}""")
        assertEquals(Country.US, place.country)
    }

    @Test fun place_roundTripsCountry() {
        val place = Place("Lagos, Faro", 37.1, -8.67, Country.PORTUGAL)
        assertEquals(place, AppJson.decodeFromString(Place.serializer(), AppJson.encodeToString(Place.serializer(), place)))
    }
}
