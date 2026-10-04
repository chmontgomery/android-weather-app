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

class TempUnitTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun conversionFromFahrenheit() {
        assertEquals(0.0, TempUnit.C.fromF(32.0), 1e-9)
        assertEquals(100.0, TempUnit.C.fromF(212.0), 1e-9)
        assertEquals(-40.0, TempUnit.C.fromF(-40.0), 1e-9)
        assertEquals(68.5, TempUnit.F.fromF(68.5), 1e-9)
    }

    @Test fun otherAndSymbol() {
        assertEquals(TempUnit.C, TempUnit.F.other())
        assertEquals(TempUnit.F, TempUnit.C.other())
        assertEquals("°C", TempUnit.C.symbol)
    }

    @Test fun dataStoreSettings_defaultsToFahrenheitAndPersists() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        val settings = DataStoreSettings(store)
        assertEquals(TempUnit.F, settings.tempUnit.first())
        settings.setTempUnit(TempUnit.C)
        assertEquals(TempUnit.C, settings.tempUnit.first())
    }

    @Test fun dataStoreSettings_unknownValueIsFahrenheit() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        store.edit { it[stringPreferencesKey("temp_unit")] = "KELVIN" }
        assertEquals(TempUnit.F, DataStoreSettings(store).tempUnit.first())
    }
}
