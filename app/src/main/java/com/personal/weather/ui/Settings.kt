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

/** Light or dark appearance; [SYSTEM] follows the phone. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** The user's display settings, remembered across launches. */
interface SettingsStore {
    val tempUnit: Flow<TempUnit>
    suspend fun setTempUnit(unit: TempUnit)
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettings(private val store: DataStore<Preferences>) : SettingsStore {
    private val unitKey = stringPreferencesKey("temp_unit")
    private val themeKey = stringPreferencesKey("theme_mode")

    override val tempUnit: Flow<TempUnit> =
        store.data.map { prefs -> prefs[unitKey]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: TempUnit.F }

    override suspend fun setTempUnit(unit: TempUnit) {
        store.edit { it[unitKey] = unit.name }
    }

    override val themeMode: Flow<ThemeMode> =
        store.data.map { prefs -> prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }
}

/** Not persisted; the default for the view model and handy in tests. */
class MemorySettings(initial: TempUnit = TempUnit.F, theme: ThemeMode = ThemeMode.SYSTEM) : SettingsStore {
    private val unit = MutableStateFlow(initial)
    private val mode = MutableStateFlow(theme)
    override val tempUnit: Flow<TempUnit> = unit
    override suspend fun setTempUnit(unit: TempUnit) { this.unit.value = unit }
    override val themeMode: Flow<ThemeMode> = mode
    override suspend fun setThemeMode(mode: ThemeMode) { this.mode.value = mode }
}
