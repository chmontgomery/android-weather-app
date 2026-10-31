package com.personal.weather.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun themeMode_cyclesSystemLightDark() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next())
    }

    @Test fun dataStoreSettings_themeDefaultsToSystemAndPersists() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        val settings = DataStoreSettings(store)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(TempUnit.F, settings.tempUnit.first())
    }

    @Test fun dataStoreSettings_unknownThemeIsSystem() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        store.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, DataStoreSettings(store).themeMode.first())
    }
}
