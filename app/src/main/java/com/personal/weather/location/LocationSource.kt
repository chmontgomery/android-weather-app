package com.personal.weather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface LocationSource {
    fun hasPermission(): Boolean

    /** Device coordinates, or null if unavailable (no permission, timeout and no last-known fix). */
    suspend fun current(): LatLon?
}

/** Platform LocationManager with coarse permission — no Play Services. */
class AndroidLocationSource(
    private val context: Context,
    private val timeoutMs: Long = 10_000,
) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun current(): LatLon? {
        if (!hasPermission()) return null
        // With coarse-only permission Android downgrades a fresh request to low power and often
        // delivers nothing before the timeout, so a recent cached fix is used straight away.
        val cached = lastKnown()
        if (cached != null && ageNanos(cached) <= MAX_CACHED_AGE_NANOS) return LatLon(cached.latitude, cached.longitude)
        // GPS is excluded: with only coarse permission it can throw SecurityException.
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }
        val fresh = provider?.let { p ->
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        manager.getCurrentLocation(p, signal, context.mainExecutor) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        val location = fresh ?: cached
        return location?.let { LatLon(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? = manager.allProviders
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos }

    private fun ageNanos(location: Location): Long = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos

    companion object {
        /** NWS grid cells are 2.5 km and coarse fixes are fuzzed to ~2 km, so a 30-minute-old fix is plenty. */
        private val MAX_CACHED_AGE_NANOS = TimeUnit.MINUTES.toNanos(30)
    }
}
