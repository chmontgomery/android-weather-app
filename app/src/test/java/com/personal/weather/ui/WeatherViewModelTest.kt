package com.personal.weather.ui

import androidx.lifecycle.SavedStateHandle
import com.personal.weather.MainDispatcherRule
import com.personal.weather.data.ForecastCache
import com.personal.weather.data.ForecastSource
import com.personal.weather.forecast.ForecastSnapshot
import com.personal.weather.forecast.TestSnapshots
import com.personal.weather.location.LatLon
import com.personal.weather.location.LocationSource
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceSearch
import com.personal.weather.location.PlaceSearchUnavailable
import com.personal.weather.location.PlaceSuggestion
import com.personal.weather.location.RecentStore
import com.personal.weather.nws.GridProperties
import com.personal.weather.nws.NumericSeries
import com.personal.weather.nws.NumericValue
import com.personal.weather.nws.NwsException
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeSource : ForecastSource {
    val calls = mutableListOf<Triple<Double, Double, String?>>()
    var respond: suspend (Double, Double, String?) -> ForecastSnapshot = { lat, lon, name ->
        TestSnapshots.snapshot(place = Place(name ?: "Blaine, MN", lat, lon))
    }

    override suspend fun fetch(lat: Double, lon: Double, name: String?): ForecastSnapshot {
        calls += Triple(lat, lon, name)
        return respond(lat, lon, name)
    }
}

private class FakeLocation(var permitted: Boolean = true, var coords: LatLon? = LatLon(45.16, -93.23)) : LocationSource {
    override fun hasPermission() = permitted
    override suspend fun current() = if (permitted) coords else null
}

private class FakeSearch : PlaceSearch {
    val calls = mutableListOf<String>()
    val resolved = mutableListOf<PlaceSuggestion>()
    var delays = mapOf<String, Long>()
    var fail = false
    var failResolve = false

    override suspend fun search(query: String): List<PlaceSuggestion> {
        calls += query
        delay(delays[query] ?: 0)
        if (fail) throw PlaceSearchUnavailable()
        return if (query == "nowhere") emptyList() else listOf(PlaceSuggestion("$query result, MN", "key-$query"))
    }

    override suspend fun resolve(suggestion: PlaceSuggestion): Place {
        resolved += suggestion
        if (failResolve) throw PlaceSearchUnavailable()
        return Place(suggestion.label, 45.83, -92.97)
    }
}

private class FakeRecents : RecentStore {
    val flow = MutableStateFlow<List<Place>>(emptyList())
    override val places: Flow<List<Place>> = flow
    override suspend fun add(place: Place) { flow.value = listOf(place) + flow.value.filter { it.name != place.name } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val source = FakeSource()
    private val location = FakeLocation()
    private val search = FakeSearch()
    private val recents = FakeRecents()
    private var now: Instant = TestSnapshots.NOW
    private val pine = Place("Pine City, MN", 45.83, -92.97)
    private val cache by lazy { ForecastCache(File(tmp.root, "forecast.json")) }

    private fun vm() = WeatherViewModel(source, location, search, recents, cache) { now }

    @Test fun start_showsCacheImmediatelyThenFreshData() = runTest {
        cache.save(TestSnapshots.snapshot(fetchedAt = now.minus(Duration.ofHours(2)), grid = TestSnapshots.grid { 10.0 }))
        source.respond = { lat, lon, _ -> TestSnapshots.snapshot(place = Place("Blaine, MN", lat, lon), grid = TestSnapshots.grid { 20.0 }) }
        val vm = vm()

        vm.start()
        assertEquals(50, vm.state.value.forecast!!.days[1].highF) // cached, before any fetch completes

        advanceUntilIdle()
        assertEquals(68, vm.state.value.forecast!!.days[1].highF)
        assertFalse(vm.state.value.loading)
        assertNull(source.calls.single().third)
    }

    @Test fun start_twiceOnlyLoadsOnce() = runTest {
        val vm = vm()
        vm.start(); vm.start()
        advanceUntilIdle()
        assertEquals(1, source.calls.size)
    }

    @Test fun start_withoutPermissionAsksThenOpensSearchIfDenied() = runTest {
        location.permitted = false
        val vm = vm()
        vm.start()
        assertTrue(vm.state.value.needsPermission)

        vm.onPermissionResult(false)
        assertFalse(vm.state.value.needsPermission)
        assertTrue(vm.state.value.search.open)
        assertEquals(WeatherViewModel.LOCATION_OFF_NOTE, vm.state.value.search.note)
        advanceUntilIdle()
        assertTrue(source.calls.isEmpty())
    }

    @Test fun permissionGrantedLoads() = runTest {
        location.permitted = false
        val vm = vm()
        vm.start()
        location.permitted = true
        vm.onPermissionResult(true)
        advanceUntilIdle()
        assertEquals(1, source.calls.size)
        assertEquals("Blaine, MN", vm.state.value.headerName)
    }

    @Test fun noLocationFixOpensSearch() = runTest {
        location.coords = null
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value.search.open)
        assertEquals(WeatherViewModel.LOCATION_OFF_NOTE, vm.state.value.search.note)
        assertFalse(vm.state.value.loading)
    }

    @Test fun fetchFails_nearCacheIsShownWithRefreshFailed() = runTest {
        val cached = TestSnapshots.snapshot(fetchedAt = now.minus(Duration.ofHours(3)))
        cache.save(cached)
        source.respond = { _, _, _ -> throw NwsException.Network(IOException("offline")) }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(cached, vm.state.value.snapshot)
        assertTrue(vm.state.value.refreshFailed)
        assertNull(vm.state.value.fatalError)
    }

    @Test fun fetchFails_farCacheIsReplacedByError() = runTest {
        cache.save(TestSnapshots.snapshot(place = pine))
        source.respond = { _, _, _ -> throw NwsException.Http(503) }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertNull(vm.state.value.snapshot)
        assertNull(vm.state.value.forecast)
        assertEquals(ErrorKind.NETWORK, vm.state.value.fatalError)
    }

    @Test fun fetchFails_noCacheIsNetworkError() = runTest {
        source.respond = { _, _, _ -> throw NwsException.Network(IOException("offline")) }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(ErrorKind.NETWORK, vm.state.value.fatalError)
    }

    @Test fun fetchFails_afterErrorFallsBackToNearCacheOnDisk() = runTest {
        // A far-away chosen place fails, then switching back to current location (near the cache) also fails.
        val cached = TestSnapshots.snapshot()
        cache.save(cached)
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        source.respond = { _, _, _ -> throw NwsException.Network(IOException("offline")) }
        vm.choosePlace(pine)
        advanceUntilIdle()
        assertEquals(ErrorKind.NETWORK, vm.state.value.fatalError)

        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(cached.place, vm.state.value.snapshot!!.place)
        assertTrue(vm.state.value.refreshFailed)
        assertNull(vm.state.value.fatalError)
    }

    @Test fun outsideUs() = runTest {
        source.respond = { _, _, _ -> throw NwsException.OutsideCoverage() }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(ErrorKind.OUTSIDE_US, vm.state.value.fatalError)
    }

    @Test fun successSavesToCache() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(vm.state.value.snapshot, cache.load())
    }

    @Test fun refreshKeepsSelectedDay() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.selectDay(2)
        now = now.plus(Duration.ofMinutes(5))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.selectedDay)
        assertEquals(LocalDate.of(2026, 10, 5), vm.state.value.forecast!!.days[2].date)
        assertEquals(2, source.calls.size)
    }

    @Test fun selectDayIsClamped() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.selectDay(99)
        assertEquals(2, vm.state.value.selectedDay)
    }

    @Test fun choosePlace_fetchesItRecordsRecentAndResetsDay() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.selectDay(1)
        vm.openSearch()

        vm.choosePlace(pine)
        advanceUntilIdle()

        assertEquals(Triple(45.83, -92.97, "Pine City, MN"), source.calls.last())
        assertEquals(pine, vm.state.value.chosen)
        assertEquals("Pine City, MN", vm.state.value.headerName)
        assertEquals(0, vm.state.value.selectedDay)
        assertFalse(vm.state.value.search.open)
        assertEquals(listOf(pine), vm.state.value.search.recents)
    }

    @Test fun chooseSuggestion_resolvesThenLoadsAndRecordsRecent() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.openSearch()
        vm.onQueryChange("pine city")
        advanceUntilIdle()

        vm.chooseSuggestion(vm.state.value.search.results.single())
        advanceUntilIdle()

        assertEquals(listOf(PlaceSuggestion("pine city result, MN", "key-pine city")), search.resolved)
        assertEquals(Triple(45.83, -92.97, "pine city result, MN"), source.calls.last())
        assertEquals(Place("pine city result, MN", 45.83, -92.97), vm.state.value.chosen)
        assertFalse(vm.state.value.search.open)
        assertFalse(vm.state.value.search.resolving)
        assertEquals(listOf(Place("pine city result, MN", 45.83, -92.97)), vm.state.value.search.recents)
    }

    @Test fun chooseSuggestion_resolveFailureKeepsSheetOpenWithMessage() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.openSearch()
        vm.onQueryChange("pine city")
        advanceUntilIdle()
        search.failResolve = true

        vm.chooseSuggestion(vm.state.value.search.results.single())
        advanceUntilIdle()

        assertTrue(vm.state.value.search.open)
        assertFalse(vm.state.value.search.resolving)
        assertEquals("Couldn't load that place — try again", vm.state.value.search.message)
        assertNull(vm.state.value.chosen)
        assertEquals(1, source.calls.size)
    }

    @Test fun recentPlaceIsChosenWithoutResolving() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.choosePlace(pine)
        advanceUntilIdle()
        assertTrue(search.resolved.isEmpty())
    }

    @Test fun useCurrentLocation_clearsChosenPlace() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        vm.choosePlace(pine)
        advanceUntilIdle()
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertNull(vm.state.value.chosen)
        assertEquals(Triple(45.16, -93.23, null), source.calls.last())
    }

    @Test fun freshViewModelStartsFromCurrentLocationEvenIfCacheIsAChosenCity() = runTest {
        cache.save(TestSnapshots.snapshot(place = pine))
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(Triple(45.16, -93.23, null), source.calls.single())
        assertEquals("Blaine, MN", vm.state.value.headerName)
    }

    @Test fun onResume_refetchesOnlyWhenStale() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()

        now = now.plus(Duration.ofMinutes(10))
        vm.onResume()
        advanceUntilIdle()
        assertEquals(1, source.calls.size)

        now = now.plus(Duration.ofMinutes(25))
        vm.onResume()
        advanceUntilIdle()
        assertEquals(2, source.calls.size)
    }

    @Test fun search_isDebounced() = runTest {
        val vm = vm()
        vm.openSearch()
        vm.onQueryChange("p")
        advanceTimeBy(100)
        vm.onQueryChange("pi")
        advanceTimeBy(100)
        vm.onQueryChange("pin")
        advanceUntilIdle()
        assertEquals(listOf("pin"), search.calls)
        assertEquals(listOf("pin result, MN"), vm.state.value.search.results.map { it.label })
    }

    @Test fun search_slowOlderQueryNeverOverwritesNewer() = runTest {
        search.delays = mapOf("pi" to 1_000L)
        val vm = vm()
        vm.openSearch()
        vm.onQueryChange("pi")
        advanceTimeBy(500) // debounce elapsed, "pi" search in flight
        vm.onQueryChange("pine")
        advanceUntilIdle()
        assertEquals(listOf("pi", "pine"), search.calls)
        assertEquals(listOf("pine result, MN"), vm.state.value.search.results.map { it.label })
    }

    @Test fun search_messages() = runTest {
        val vm = vm()
        vm.openSearch()
        vm.onQueryChange("nowhere")
        advanceUntilIdle()
        assertEquals("No matching US places", vm.state.value.search.message)

        search.fail = true
        vm.onQueryChange("pine")
        advanceUntilIdle()
        assertEquals("Search unavailable — check your connection", vm.state.value.search.message)
        assertTrue(vm.state.value.search.results.isEmpty())
    }

    @Test fun search_blankQueryClearsResults() = runTest {
        val vm = vm()
        vm.openSearch()
        vm.onQueryChange("pine")
        advanceUntilIdle()
        vm.onQueryChange("  ")
        advanceUntilIdle()
        assertTrue(vm.state.value.search.results.isEmpty())
        assertEquals(1, search.calls.size)
    }

    @Test fun choosePlace_farFromShownData_clearsItWhileLoadingUnderNewName() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals("Blaine, MN", vm.state.value.headerName)

        val gate = CompletableDeferred<Unit>()
        source.respond = { lat, lon, name ->
            gate.await()
            TestSnapshots.snapshot(place = Place(name ?: "Blaine, MN", lat, lon))
        }
        vm.choosePlace(pine)
        advanceUntilIdle() // fetch is now pending on the gate
        assertNull(vm.state.value.snapshot)
        assertNull(vm.state.value.forecast)
        assertEquals("Pine City, MN", vm.state.value.headerName)
        assertTrue(vm.state.value.loading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(pine.name, vm.state.value.snapshot!!.place.name)
        assertFalse(vm.state.value.loading)
    }

    @Test fun choosePlace_nearShownData_keepsItWhileLoading() = runTest {
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        val shown = vm.state.value.snapshot
        source.respond = { _, _, _ -> CompletableDeferred<Nothing>().await() }
        vm.choosePlace(Place("Blaine, MN", 45.17, -93.23))
        advanceUntilIdle()
        assertEquals(shown, vm.state.value.snapshot)
    }

    @Test fun onResume_whilePermissionRequestPending_doesNothing() = runTest {
        cache.save(TestSnapshots.snapshot(fetchedAt = now.minus(Duration.ofHours(2))))
        location.permitted = false
        val vm = vm()
        vm.start()
        vm.onResume()
        advanceUntilIdle()
        assertTrue(source.calls.isEmpty())
        assertFalse(vm.state.value.search.open)
        assertTrue(vm.state.value.needsPermission)
    }

    private fun badSnapshot() = TestSnapshots.snapshot(
        grid = GridProperties(temperature = NumericSeries("wmoUnit:degC", listOf(NumericValue("garbage", 10.0)))),
    )

    @Test fun fetch_unbuildableSnapshotIsNotCachedAndFallsBackToGoodCache() = runTest {
        cache.save(TestSnapshots.snapshot(fetchedAt = now.minus(Duration.ofHours(2)), grid = TestSnapshots.grid { 10.0 }))
        source.respond = { _, _, _ -> badSnapshot() }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value.refreshFailed)
        assertEquals(50, vm.state.value.forecast!!.days[1].highF)
        assertEquals(50, ForecastCacheProbe.highOfCache(cache))
    }

    @Test fun fetch_unbuildableSnapshotWithNoCacheIsNetworkError() = runTest {
        source.respond = { _, _, _ -> badSnapshot() }
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(ErrorKind.NETWORK, vm.state.value.fatalError)
        assertNull(cache.load())
    }

    @Test fun start_unbuildableCacheDoesNotThrowAndStillLoads() = runTest {
        cache.save(badSnapshot())
        val vm = vm()
        vm.start()
        assertNull(vm.state.value.forecast)
        advanceUntilIdle()
        assertEquals(1, source.calls.size)
        assertEquals("Blaine, MN", vm.state.value.headerName)
    }

    @Test fun restoredChosenPlace_isLoadedByStartInsteadOfCurrentLocation() = runTest {
        val saved = SavedStateHandle(mapOf("chosen_name" to pine.name, "chosen_lat" to pine.lat, "chosen_lon" to pine.lon))
        val vm = WeatherViewModel(source, location, search, recents, cache, saved) { now }
        assertEquals(pine, vm.state.value.chosen)
        vm.start()
        advanceUntilIdle()
        assertEquals(Triple(pine.lat, pine.lon, pine.name), source.calls.single())
    }

    @Test fun choosePlaceWritesHandle_useCurrentLocationClearsIt() = runTest {
        val saved = SavedStateHandle()
        val vm = WeatherViewModel(source, location, search, recents, cache, saved) { now }
        vm.choosePlace(pine)
        advanceUntilIdle()
        assertEquals(pine.name, saved.get<String>("chosen_name"))
        assertEquals(pine.lat, saved.get<Double>("chosen_lat")!!, 0.0)
        // A new VM over the same handle (process death) restores it.
        assertEquals(pine, WeatherViewModel(source, location, search, recents, cache, saved) { now }.state.value.chosen)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertNull(saved.get<String>("chosen_name"))
        assertNull(WeatherViewModel(source, location, search, recents, cache, saved) { now }.state.value.chosen)
    }

    @Test fun permissionRequest_isOneShotUntilResult() = runTest {
        val vm = vm()
        assertTrue(vm.consumePermissionRequest())
        assertFalse(vm.consumePermissionRequest())
        vm.onPermissionResult(true)
        assertTrue(vm.consumePermissionRequest())
    }

    @Test fun onClockTick_rollsDayZeroOverAtMidnight() = runTest {
        now = Instant.parse("2026-10-04T04:59:00Z") // 11:59 PM CDT Oct 3
        val vm = vm()
        vm.start()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 3), vm.state.value.forecast!!.days.first().date)
        now = Instant.parse("2026-10-04T05:01:00Z") // 12:01 AM Oct 4
        vm.onClockTick()
        assertEquals(LocalDate.of(2026, 10, 4), vm.state.value.forecast!!.days.first().date)
    }

    @Test fun corruptRecents_doesNotCrash() = runTest {
        val broken = object : RecentStore {
            override val places: Flow<List<Place>> = kotlinx.coroutines.flow.flow { throw IOException("corrupt") }
            override suspend fun add(place: Place) {}
        }
        val vm = WeatherViewModel(source, location, search, broken, cache) { now }
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.state.value.search.recents.isEmpty())
    }
}

/** Reads the cached snapshot's tomorrow-high through a fresh build. */
private object ForecastCacheProbe {
    fun highOfCache(cache: ForecastCache): Int? =
        cache.load()?.let { com.personal.weather.forecast.ForecastBuilder.build(it, TestSnapshots.NOW).days[1].highF }
}

/** Production's Main.immediate runs a launched body eagerly; Unconfined reproduces that. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelEagerDispatchTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())
    @get:Rule val tmp = TemporaryFolder()

    @Test fun loadThatNeverSuspends_stillClearsLoading() = runTest {
        val location = FakeLocation(coords = null)
        val vm = WeatherViewModel(FakeSource(), location, FakeSearch(), FakeRecents(), ForecastCache(File(tmp.root, "f.json"))) { TestSnapshots.NOW }
        vm.start()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.search.open)
        assertEquals(WeatherViewModel.LOCATION_OFF_NOTE, vm.state.value.search.note)
    }
}
