package com.example.media

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<ResolvedLocation> = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location: Location? = runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            }.getOrNull()
                ?: runCatching { client.lastLocation.await() }.getOrNull()
                ?: fallbackLocationManager(context)

            if (location == null) {
                return@withContext Result.failure(
                    IllegalStateException("GPS fix unavailable yet. You can enter a place name manually below.")
                )
            }
            val label = reverseGeocode(context, location.latitude, location.longitude)
            Result.success(
                ResolvedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    label = label
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String {
        val coordsFallback = String.format(Locale.US, "%.4f, %.4f", lat, lng)
        if (!Geocoder.isPresent()) return coordsFallback
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { list ->
                        if (cont.isActive) cont.resume(list.firstOrNull())
                    }
                }
            } else {
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            }
            if (address != null) {
                listOfNotNull(
                    address.featureName?.takeIf { !it.all { ch -> ch.isDigit() } },
                    address.subLocality,
                    address.locality ?: address.subAdminArea,
                    address.adminArea
                ).distinct().take(2).joinToString(", ").ifBlank { coordsFallback }
            } else {
                coordsFallback
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            coordsFallback
        }
    }
}
