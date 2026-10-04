package com.personal.weather.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Cached data within this distance counts as "the same place". */
    const val SAME_PLACE_KM = 10.0
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    fun isNear(place: Place, lat: Double, lon: Double): Boolean =
        distanceKm(place.lat, place.lon, lat, lon) <= SAME_PLACE_KM
}
