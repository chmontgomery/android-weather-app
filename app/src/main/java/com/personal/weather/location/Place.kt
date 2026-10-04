package com.personal.weather.location

import kotlinx.serialization.Serializable

/**
 * A named location. For the device's own location, [lat]/[lon] are the device coordinates. [country] picks the
 * forecast source; it defaults to US so data saved before Portugal support still loads.
 */
@Serializable
data class Place(val name: String, val lat: Double, val lon: Double, val country: Country = Country.US)

data class LatLon(val lat: Double, val lon: Double)
