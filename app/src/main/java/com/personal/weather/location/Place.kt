package com.personal.weather.location

import kotlinx.serialization.Serializable

/** A named location. For the device's own location, [lat]/[lon] are the device coordinates and [name] comes from NWS. */
@Serializable
data class Place(val name: String, val lat: Double, val lon: Double)

data class LatLon(val lat: Double, val lon: Double)
