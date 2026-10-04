package com.personal.weather.location

import kotlinx.serialization.Serializable

/** Countries the app supports; each has its own forecast source. */
@Serializable
enum class Country {
    US,
    PORTUGAL;

    companion object {
        private data class Box(val south: Double, val north: Double, val west: Double, val east: Double) {
            fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east
        }

        // Mainland, Madeira, Azores.
        private val PORTUGAL_AREAS = listOf(
            Box(36.8, 42.2, -9.6, -6.1),
            Box(32.3, 33.2, -17.4, -16.2),
            Box(36.8, 39.8, -31.4, -24.9),
        )

        /** Portugal if inside one of its areas; otherwise US (weather.gov then reports anything outside its coverage). */
        fun of(lat: Double, lon: Double): Country =
            if (PORTUGAL_AREAS.any { it.contains(lat, lon) }) PORTUGAL else US
    }
}
