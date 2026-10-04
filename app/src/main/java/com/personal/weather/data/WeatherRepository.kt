package com.personal.weather.data

import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.location.Country
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceNamer
import com.personal.weather.nws.NwsClient
import com.personal.weather.nws.NwsException
import com.personal.weather.openmeteo.OpenMeteoClient
import com.personal.weather.openmeteo.OpenMeteoException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface ForecastSource {
    /** [name] null means "name it after where it is". Throws on failure. */
    suspend fun fetch(lat: Double, lon: Double, name: String?, country: Country): ForecastSnapshot
}

/** US → weather.gov; Portugal → Open-Meteo (named via [namer], since Open-Meteo has no place names). */
class WeatherRepository(
    private val nws: NwsClient,
    private val openMeteo: OpenMeteoClient,
    private val namer: PlaceNamer,
    private val clock: () -> Instant = Instant::now,
) : ForecastSource {
    override suspend fun fetch(lat: Double, lon: Double, name: String?, country: Country): ForecastSnapshot = when (country) {
        Country.US -> fetchNws(lat, lon, name)
        Country.PORTUGAL -> fetchOpenMeteo(lat, lon, name)
    }

    private suspend fun fetchNws(lat: Double, lon: Double, name: String?): ForecastSnapshot = coroutineScope {
        val points = nws.points(lat, lon)
        val grid = async { nws.gridpoints(points.forecastGridData) }
        val observation = async { nws.latestObservation(points.observationStations) }
        val gridData = grid.await()
        // A 200 with no temperatures would overwrite a good cache with an empty forecast; treat it as a failure.
        if (gridData.temperature.values.none { it.value != null }) {
            throw NwsException.BadResponse(IllegalStateException("empty forecast"))
        }
        val label = name ?: points.relativeLocation.properties.let { "${it.city}, ${it.state}" }
        ForecastSnapshot.Nws(
            place = Place(label, lat, lon, Country.US),
            fetchedAtEpochMs = clock().toEpochMilli(),
            timeZone = points.timeZone,
            grid = gridData,
            observation = observation.await(),
        )
    }

    private suspend fun fetchOpenMeteo(lat: Double, lon: Double, name: String?): ForecastSnapshot = coroutineScope {
        val label = async { name ?: lookUpName(lat, lon) }
        val response = openMeteo.forecast(lat, lon)
        if (response.hourly.temperature.none { it != null }) throw OpenMeteoException("empty forecast")
        ForecastSnapshot.OpenMeteo(
            place = Place(label.await(), lat, lon, Country.PORTUGAL),
            fetchedAtEpochMs = clock().toEpochMilli(),
            timeZone = response.timezone,
            hourly = response.hourly,
            current = response.current,
        )
    }

    /** A failed or empty lookup must not block the forecast. */
    private suspend fun lookUpName(lat: Double, lon: Double): String = try {
        namer.nameFor(lat, lon) ?: UNNAMED
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UNNAMED
    }

    companion object {
        const val UNNAMED = "Current location"
    }
}
