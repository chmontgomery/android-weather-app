package com.personal.weather

import android.Manifest
import android.app.UiModeManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.weather.data.ForecastCache
import com.personal.weather.data.WeatherRepository
import com.personal.weather.location.AndroidLocationSource
import com.personal.weather.location.ArcGisPlaceSearch
import com.personal.weather.location.DataStoreRecentPlaces
import com.personal.weather.location.recentsDataStore
import com.personal.weather.nws.NwsClient
import com.personal.weather.openmeteo.OpenMeteoClient
import com.personal.weather.ui.DataStoreSettings
import com.personal.weather.ui.WeatherScreen
import com.personal.weather.ui.settingsDataStore
import com.personal.weather.ui.ThemeMode
import com.personal.weather.ui.WeatherViewModel
import com.personal.weather.ui.theme.WeatherTheme
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        val factory = viewModelFactory {
            initializer {
                val http = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val search = ArcGisPlaceSearch(http)
                // Naming must not hold up the forecast: short overall timeout, falls back to "Current location".
                val namer = ArcGisPlaceSearch(http.newBuilder().callTimeout(5, TimeUnit.SECONDS).build())
                WeatherViewModel(
                    source = WeatherRepository(NwsClient(http), OpenMeteoClient(http), namer),
                    location = AndroidLocationSource(appContext),
                    placeSearch = search,
                    recents = DataStoreRecentPlaces(appContext.recentsDataStore),
                    cache = ForecastCache(File(appContext.filesDir, "forecast.json")),
                    saved = createSavedStateHandle(),
                    settings = DataStoreSettings(appContext.settingsDataStore),
                )
            }
        }

        // The theme setting is applied as Android's per-app night mode, which the system remembers and uses for the
        // splash screen and the first frame. The manifest's configChanges="uiMode" lets it switch without recreation.
        val uiModes = getSystemService(UiModeManager::class.java)

        setContent {
            val vm: WeatherViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                // Without recreation, the bar icons have to be told when the theme flips.
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            WeatherTheme {
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    vm.onPermissionResult(granted)
                }

                LaunchedEffect(Unit) { vm.start() }
                LaunchedEffect(state.needsPermission) {
                    if (state.needsPermission && vm.consumePermissionRequest()) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

                WeatherScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onClockTick = vm::onClockTick,
                    onSelectDay = vm::selectDay,
                    onOpenSearch = { vm.openSearch() },
                    onQueryChange = vm::onQueryChange,
                    onPickPlace = vm::choosePlace,
                    onPickSuggestion = vm::chooseSuggestion,
                    onUseCurrentLocation = vm::useCurrentLocation,
                    onCloseSearch = vm::closeSearch,
                    onToggleTempUnit = vm::toggleTempUnit,
                    onCycleThemeMode = { uiModes.setApplicationNightMode(vm.cycleThemeMode().nightMode) },
                )
            }
        }
    }
}

private val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
