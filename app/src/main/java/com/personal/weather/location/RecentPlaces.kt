package com.personal.weather.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.weather.AppJson
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class RecentEntry(val place: Place, val lastUsedEpochMs: Long)

/** Pure list rules: newest first, unique by name, at most [MAX], nothing older than [MAX_AGE]. */
object RecentList {
    const val MAX = 10
    val MAX_AGE: Duration = Duration.ofDays(30)

    fun prune(entries: List<RecentEntry>, now: Instant): List<RecentEntry> =
        entries.filter { now.toEpochMilli() - it.lastUsedEpochMs <= MAX_AGE.toMillis() }

    fun add(entries: List<RecentEntry>, place: Place, now: Instant): List<RecentEntry> =
        prune(listOf(RecentEntry(place, now.toEpochMilli())) + entries.filter { it.place.name != place.name }, now)
            .take(MAX)
}

interface RecentStore {
    val places: Flow<List<Place>>
    suspend fun add(place: Place)
}

val Context.recentsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recents")

class DataStoreRecentPlaces(
    private val store: DataStore<Preferences>,
    private val clock: () -> Instant = Instant::now,
) : RecentStore {
    private val key = stringPreferencesKey("recent_places")
    private val serializer = ListSerializer(RecentEntry.serializer())

    override val places: Flow<List<Place>> =
        store.data.map { prefs -> RecentList.prune(decode(prefs[key]), clock()).map { it.place } }

    override suspend fun add(place: Place) {
        store.edit { prefs -> prefs[key] = AppJson.encodeToString(serializer, RecentList.add(decode(prefs[key]), place, clock())) }
    }

    private fun decode(raw: String?): List<RecentEntry> =
        raw?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
}
