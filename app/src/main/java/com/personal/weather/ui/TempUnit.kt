package com.personal.weather.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** How temperatures are shown. Forecasts stay in °F internally; this converts only for display. */
enum class TempUnit {
    F,
    C;

    fun fromF(f: Double): Double = if (this == F) f else (f - 32.0) * 5.0 / 9.0

    val symbol: String get() = "°$name"

    fun other(): TempUnit = if (this == F) C else F
}

/** The user's display settings, remembered across launches. */
interface SettingsStore {
    val tempUnit: Flow<TempUnit>
    suspend fun setTempUnit(unit: TempUnit)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettings(private val store: DataStore<Preferences>) : SettingsStore {
    private val key = stringPreferencesKey("temp_unit")

    override val tempUnit: Flow<TempUnit> =
        store.data.map { prefs -> prefs[key]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: TempUnit.F }

    override suspend fun setTempUnit(unit: TempUnit) {
        store.edit { it[key] = unit.name }
    }
}

/** Not persisted; the default for the view model and handy in tests. */
class MemorySettings(initial: TempUnit = TempUnit.F) : SettingsStore {
    private val unit = MutableStateFlow(initial)
    override val tempUnit: Flow<TempUnit> = unit
    override suspend fun setTempUnit(unit: TempUnit) { this.unit.value = unit }
}
