package com.personal.weather.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.weather.data.ForecastCache
import com.personal.weather.data.ForecastSource
import com.personal.weather.forecast.Forecast
import com.personal.weather.forecast.ForecastBuilder
import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.location.Geo
import com.personal.weather.location.LatLon
import com.personal.weather.location.LocationSource
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceSearch
import com.personal.weather.location.PlaceSuggestion
import com.personal.weather.location.RecentStore
import com.personal.weather.nws.NwsException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

enum class ErrorKind { OUTSIDE_US, NETWORK }

data class SearchState(
    val open: Boolean = false,
    val note: String? = null,
    val query: String = "",
    val results: List<PlaceSuggestion> = emptyList(),
    val message: String? = null,
    val recents: List<Place> = emptyList(),
    /** True while a picked suggestion's coordinates are being looked up. */
    val resolving: Boolean = false,
)

data class WeatherUiState(
    /** A place picked from search; null means "use the device location". Not persisted — a fresh launch starts at current location. */
    val chosen: Place? = null,
    val snapshot: ForecastSnapshot? = null,
    val forecast: Forecast? = null,
    val selectedDay: Int = 0,
    val loading: Boolean = false,
    /** True when the shown data is older than a refresh that just failed. */
    val refreshFailed: Boolean = false,
    /** Set when there's nothing usable to show. */
    val fatalError: ErrorKind? = null,
    val needsPermission: Boolean = false,
    val search: SearchState = SearchState(),
) {
    val headerName: String get() = chosen?.name ?: snapshot?.place?.name ?: "Current location"
}

class WeatherViewModel(
    private val source: ForecastSource,
    private val location: LocationSource,
    private val placeSearch: PlaceSearch,
    private val recents: RecentStore,
    private val cache: ForecastCache,
    /** Keeps the chosen place across process death / activity recreation (but not a fresh launch). */
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(WeatherUiState(chosen = restoreChosen()))
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var started = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            recents.places.catch { emit(emptyList()) }.collect { list -> _state.update { it.copy(search = it.search.copy(recents = list)) } }
        }
    }

    /** Called once per activity creation; only the first call does anything for this ViewModel. */
    fun start() {
        if (started) return
        started = true
        val chosen = _state.value.chosen
        // An unbuildable cache is ignored; a cache for somewhere else is never shown under the chosen place's name.
        cache.load()
            ?.takeIf { chosen == null || Geo.isNear(it.place, chosen.lat, chosen.lon) }
            ?.let { runCatching { show(it, keepDay = false, refreshFailed = false) } }
        if (chosen != null || location.hasPermission()) load(keepDay = false) else _state.update { it.copy(needsPermission = true) }
    }

    /**
     * One-shot gate for the runtime permission dialog. The flag lives in the SavedStateHandle so an activity
     * recreated while the dialog is up doesn't launch a second request (whose empty answer would look like a denial).
     */
    fun consumePermissionRequest(): Boolean {
        if (saved.get<Boolean>(KEY_PERMISSION_IN_FLIGHT) == true) return false
        saved[KEY_PERMISSION_IN_FLIGHT] = true
        return true
    }

    fun onPermissionResult(granted: Boolean) {
        saved[KEY_PERMISSION_IN_FLIGHT] = false
        _state.update { it.copy(needsPermission = false) }
        if (granted) load(keepDay = false) else openSearch(LOCATION_OFF_NOTE)
    }

    /** Refetch when data is stale; otherwise just rebuild so "today" and the now-line stay current. */
    fun onResume() {
        val snapshot = _state.value.snapshot ?: return
        if (loadJob?.isActive == true) return
        // A permission request is pending (or location is unavailable): don't reload or pop the search sheet.
        if (_state.value.needsPermission) return
        if (_state.value.chosen == null && !location.hasPermission()) return
        if (Duration.between(snapshot.fetchedAt, clock()) > STALE_AFTER) {
            load(keepDay = true)
        } else {
            show(snapshot, keepDay = true, refreshFailed = _state.value.refreshFailed)
        }
    }

    /** Called when the local date may have rolled over while the app stays open. */
    fun onClockTick() {
        val snapshot = _state.value.snapshot ?: return
        runCatching { show(snapshot, keepDay = true, refreshFailed = _state.value.refreshFailed) }
    }

    fun refresh() = load(keepDay = true)

    fun selectDay(index: Int) {
        _state.update { s ->
            val last = (s.forecast?.days?.size ?: 1) - 1
            s.copy(selectedDay = index.coerceIn(0, maxOf(0, last)))
        }
    }

    fun openSearch(note: String? = null) {
        _state.update { it.copy(search = it.search.copy(open = true, note = note, query = "", results = emptyList(), message = null)) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        resolveJob?.cancel()
        _state.update { it.copy(search = it.search.copy(open = false, note = null, resolving = false)) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query, message = null, resolving = false)) }
        searchJob?.cancel()
        resolveJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(search = it.search.copy(results = emptyList())) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val (results, message) = try {
                val found = placeSearch.search(query.trim())
                found to (if (found.isEmpty()) "No matching US places" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList<PlaceSuggestion>() to "Search unavailable — check your connection"
            }
            _state.update { it.copy(search = it.search.copy(results = results, message = message)) }
        }
    }

    /** A search result was tapped: look up its coordinates, then load it like any chosen place. */
    fun chooseSuggestion(suggestion: PlaceSuggestion) {
        resolveJob?.cancel()
        _state.update { it.copy(search = it.search.copy(resolving = true, message = null)) }
        resolveJob = viewModelScope.launch {
            val place = try {
                placeSearch.resolve(suggestion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(search = it.search.copy(resolving = false, message = "Couldn't load that place — try again")) }
                return@launch
            }
            _state.update { it.copy(search = it.search.copy(resolving = false)) }
            choosePlace(place)
        }
    }

    fun choosePlace(place: Place) {
        closeSearch()
        saveChosen(place)
        _state.update { s ->
            // Never show one place's data under another place's name.
            val keep = s.snapshot?.let { Geo.isNear(it.place, place.lat, place.lon) } == true
            if (keep) {
                s.copy(chosen = place)
            } else {
                s.copy(chosen = place, snapshot = null, forecast = null, refreshFailed = false, fatalError = null)
            }
        }
        viewModelScope.launch { recents.add(place) }
        load(keepDay = false)
    }

    fun useCurrentLocation() {
        closeSearch()
        saveChosen(null)
        _state.update { it.copy(chosen = null) }
        if (location.hasPermission()) load(keepDay = false) else _state.update { it.copy(needsPermission = true) }
    }

    private fun load(keepDay: Boolean) {
        loadJob?.cancel()
        // LAZY + explicit start: Main.immediate may run the body eagerly, before loadJob is assigned.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _state.update { it.copy(loading = true) }
            try {
                val chosen = _state.value.chosen
                val coords = if (chosen != null) LatLon(chosen.lat, chosen.lon) else location.current()
                if (coords == null) {
                    openSearch(LOCATION_OFF_NOTE)
                    return@launch
                }
                fetch(coords, chosen?.name, keepDay)
            } finally {
                // A newer load may have replaced this one; only the current load clears the spinner.
                if (loadJob === coroutineContext.job) _state.update { it.copy(loading = false) }
            }
        }
        loadJob = job
        job.start()
    }

    private suspend fun fetch(coords: LatLon, name: String?, keepDay: Boolean) {
        try {
            val snapshot = source.fetch(coords.lat, coords.lon, name)
            // Build before caching: a snapshot we can't build must never reach the cache.
            val forecast = ForecastBuilder.build(snapshot, clock())
            runCatching { cache.save(snapshot) }
            show(snapshot, keepDay, refreshFailed = false, forecast = forecast)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NwsException.OutsideCoverage) {
            _state.update { it.copy(snapshot = null, forecast = null, refreshFailed = false, fatalError = ErrorKind.OUTSIDE_US) }
        } catch (e: Exception) {
            val shown = sequenceOf({ _state.value.snapshot }, { cache.load() })
                .mapNotNull { it()?.takeIf { s -> Geo.isNear(s.place, coords.lat, coords.lon) } }
                .any { runCatching { show(it, keepDay, refreshFailed = true) }.isSuccess }
            if (!shown) {
                _state.update { it.copy(snapshot = null, forecast = null, refreshFailed = false, fatalError = ErrorKind.NETWORK) }
            }
        }
    }

    private fun restoreChosen(): Place? {
        val name = saved.get<String>(KEY_CHOSEN_NAME) ?: return null
        val lat = saved.get<Double>(KEY_CHOSEN_LAT) ?: return null
        val lon = saved.get<Double>(KEY_CHOSEN_LON) ?: return null
        return Place(name, lat, lon)
    }

    private fun saveChosen(place: Place?) {
        saved[KEY_CHOSEN_NAME] = place?.name
        saved[KEY_CHOSEN_LAT] = place?.lat
        saved[KEY_CHOSEN_LON] = place?.lon
    }

    private fun show(
        snapshot: ForecastSnapshot,
        keepDay: Boolean,
        refreshFailed: Boolean,
        forecast: Forecast = ForecastBuilder.build(snapshot, clock()),
    ) {
        _state.update { s ->
            val previousDate = s.forecast?.days?.getOrNull(s.selectedDay)?.date
            val index = if (keepDay && previousDate != null) {
                forecast.days.indexOfFirst { it.date == previousDate }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            s.copy(snapshot = snapshot, forecast = forecast, selectedDay = index, refreshFailed = refreshFailed, fatalError = null)
        }
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(30)
        private const val KEY_CHOSEN_NAME = "chosen_name"
        private const val KEY_CHOSEN_LAT = "chosen_lat"
        private const val KEY_CHOSEN_LON = "chosen_lon"
        private const val KEY_PERMISSION_IN_FLIGHT = "permission_in_flight"
        const val SEARCH_DEBOUNCE_MS = 400L
        const val LOCATION_OFF_NOTE = "Location is off — search for a city."
    }
}
