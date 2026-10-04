package com.personal.weather.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidLocationSourceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(LocationManager::class.java)

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
    }

    private fun fixAgedMinutes(minutes: Long) = Location(LocationManager.NETWORK_PROVIDER).apply {
        latitude = 45.19
        longitude = -93.15
        time = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes)
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - TimeUnit.MINUTES.toNanos(minutes)
    }

    @Test fun recentLastKnownFixIsUsedWithoutWaitingForAFreshOne() = runTest {
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fixAgedMinutes(1))

        val result = AndroidLocationSource(app).current()

        assertEquals(LatLon(45.19, -93.15), result)
        assertEquals("should not wait on a fresh fix", 0L, currentTime)
    }

    @Test fun staleLastKnownFixWaitsForFreshThenFallsBack() = runTest {
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fixAgedMinutes(120))

        val result = AndroidLocationSource(app, timeoutMs = 5_000).current()

        assertEquals(LatLon(45.19, -93.15), result)
        assertEquals(5_000L, currentTime)
    }
}
