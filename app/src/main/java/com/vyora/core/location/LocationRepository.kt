package com.vyora.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(private val context: Context) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _coordinates = MutableStateFlow<LocationCoordinates?>(
        LocationCoordinates(37.7749, -122.4194, 15f, 42.0)
    )
    override val coordinates: StateFlow<LocationCoordinates?> = _coordinates.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentFix(): LocationCoordinates? {
        return try {
            val tokenSource = CancellationTokenSource()
            val loc = suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
            if (loc != null) {
                val coords = LocationCoordinates(loc.latitude, loc.longitude, loc.accuracy, loc.altitude, loc.time)
                _coordinates.value = coords
                coords
            } else {
                _coordinates.value
            }
        } catch (e: Exception) {
            _coordinates.value
        }
    }
}
