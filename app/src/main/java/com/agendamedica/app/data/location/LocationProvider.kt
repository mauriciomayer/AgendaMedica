package com.agendamedica.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import com.agendamedica.app.domain.search.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Source of the device's current position. Returns null when no position is available. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

/**
 * Position via the platform LocationManager (androidx.core `LocationManagerCompat`) — no Play
 * Services. Tries the last known fix first, then a single fresh fix with a timeout.
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission") // checked in hasPermission() before any location call
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null

        return try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (providers.isEmpty()) return null

            // A days-old fix would order the results by the wrong place, so only a recent one is reused.
            val now = System.currentTimeMillis()
            val lastKnown = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .filter { now - it.time <= MAX_LAST_KNOWN_AGE_MS }
                .maxByOrNull { it.time }
            if (lastKnown != null) return lastKnown.toGeoPoint()

            // Each enabled provider gets its own chance (network first, then GPS).
            for (provider in providers) {
                val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = androidx.core.os.CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        val executor = Executor { it.run() }
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            executor,
                            Consumer<Location?> { location -> if (cont.isActive) cont.resume(location) },
                        )
                    }
                }
                if (fix != null) return fix.toGeoPoint()
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val FIX_TIMEOUT_MS = 6_000L
        const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L
    }
}
