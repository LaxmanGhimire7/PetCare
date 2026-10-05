package com.example.petcare.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared geocoding and distance helpers; only fused location depends on Play services. */
object PlaceLocation {
    fun playServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Great-circle distance in kilometres, suitable for sorting nearby places. */
    fun distanceKm(latA: Double, lonA: Double, latB: Double, lonB: Double): Double {
        val radius = 6371.0
        val dLat = Math.toRadians(latB - latA)
        val dLon = Math.toRadians(lonB - lonA)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latA)) *
            cos(Math.toRadians(latB)) * sin(dLon / 2).pow(2)
        return 2 * radius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** API 33 uses the asynchronous listener; old Android versions geocode on IO. */
    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val coder = Geocoder(context)
        return try {
            val address = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { continuation ->
                    coder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { coder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }
            }
            address?.getAddressLine(0)
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }

    /** Search uses platform geocoding so it requires no Places billing account. */
    suspend fun search(context: Context, query: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent() || query.isBlank()) return null
        val coder = Geocoder(context)
        return try {
            val address = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { continuation ->
                    coder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { coder.getFromLocationName(query, 1)?.firstOrNull() }
            }
            address?.let { it.latitude to it.longitude }
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }
}
