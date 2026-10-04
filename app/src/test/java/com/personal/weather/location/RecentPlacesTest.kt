package com.personal.weather.location

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentPlacesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val now = Instant.parse("2026-10-03T19:00:00Z")
    private val pine = Place("Pine City, MN", 45.83, -92.97)
    private val duluth = Place("Duluth, MN", 46.79, -92.10)

    @Test fun add_putsNewestFirstAndDedupesByName() {
        var list = RecentList.add(emptyList(), pine, now)
        list = RecentList.add(list, duluth, now.plusSeconds(60))
        list = RecentList.add(list, pine.copy(lat = 45.84), now.plusSeconds(120))
        assertEquals(listOf("Pine City, MN", "Duluth, MN"), list.map { it.place.name })
        assertEquals(45.84, list.first().place.lat, 0.0)
    }

    @Test fun prune_dropsEntriesOlderThan30Days() {
        val list = listOf(
            RecentEntry(pine, now.minus(Duration.ofDays(29)).toEpochMilli()),
            RecentEntry(duluth, now.minus(Duration.ofDays(31)).toEpochMilli()),
        )
        assertEquals(listOf(pine), RecentList.prune(list, now).map { it.place })
    }

    @Test fun add_capsAtTen() {
        var list = emptyList<RecentEntry>()
        repeat(12) { i -> list = RecentList.add(list, Place("City $i, MN", 45.0, -93.0), now.plusSeconds(i.toLong())) }
        assertEquals(RecentList.MAX, list.size)
        assertEquals("City 11, MN", list.first().place.name)
    }

    @Test fun dataStore_persistsAndPrunes() = runTest {
        var clock = now
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "recents.preferences_pb") }
        val recents = DataStoreRecentPlaces(store) { clock }

        recents.add(pine)
        assertEquals(listOf(pine), recents.places.first())

        clock = now.plus(Duration.ofDays(31))
        recents.add(duluth)
        assertEquals(listOf(duluth), recents.places.first())
    }
}
