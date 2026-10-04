package com.personal.weather.data

import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.location.Place
import com.personal.weather.nws.NwsClient
import com.personal.weather.nws.NwsException
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface ForecastSource {
    /** [name] null means "name it after NWS's nearest city". Throws NwsException on failure. */
    suspend fun fetch(lat: Double, lon: Double, name: String?): ForecastSnapshot
}

class WeatherRepository(
    private val client: NwsClient,
    private val clock: () -> Instant = Instant::now,
) : ForecastSource {
    override suspend fun fetch(lat: Double, lon: Double, name: String?): ForecastSnapshot = coroutineScope {
        val points = client.points(lat, lon)
        val grid = async { client.gridpoints(points.forecastGridData) }
        val observation = async { client.latestObservation(points.observationStations) }
        val gridData = grid.await()
        // A 200 with no temperatures would overwrite a good cache with an empty forecast; treat it as a failure.
        if (gridData.temperature.values.none { it.value != null }) {
            throw NwsException.BadResponse(IllegalStateException("empty forecast"))
        }
        val label = name ?: points.relativeLocation.properties.let { "${it.city}, ${it.state}" }
        ForecastSnapshot(
            place = Place(label, lat, lon),
            fetchedAtEpochMs = clock().toEpochMilli(),
            timeZone = points.timeZone,
            grid = gridData,
            observation = observation.await(),
        )
    }
}
