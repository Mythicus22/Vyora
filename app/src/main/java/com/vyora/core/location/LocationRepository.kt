package com.vyora.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(private val context: Context) : LocationProvider {

    companion object {
        private const val TAG = "LocationRepository"
    }

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Initialized to null - no hardcoded San Francisco default
    private val _coordinates = MutableStateFlow<LocationCoordinates?>(null)
    override val coordinates: StateFlow<LocationCoordinates?> = _coordinates.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null
    private var isUpdating = false

    init {
        if (hasLocationPermission()) {
            fetchLastKnownLocation()
            startLocationUpdates()
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation() {
        if (!hasLocationPermission()) return

        // 1. Try Google Play Services fused client cached location
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateLocation(loc)
                } else {
                    checkNativeLastKnownLocation()
                }
            }.addOnFailureListener {
                checkNativeLastKnownLocation()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query fusedClient lastLocation: ${e.message}")
            checkNativeLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkNativeLastKnownLocation() {
        if (!hasLocationPermission()) return
        val mgr = locationManager ?: return

        try {
            var bestLoc: Location? = null
            if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLoc = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (bestLoc == null && mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLoc = mgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (bestLoc == null && mgr.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLoc = mgr.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
            if (bestLoc != null) {
                updateLocation(bestLoc)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query native last known location: ${e.message}")
        }
    }

    private fun updateLocation(loc: Location) {
        val coords = LocationCoordinates(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            altitude = loc.altitude,
            timestamp = if (loc.time > 0) loc.time else System.currentTimeMillis()
        )
        _coordinates.value = coords
        Log.i(TAG, "Acquired real user location: (${loc.latitude}, ${loc.longitude}) ±${loc.accuracy}m")
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates() {
        if (!hasLocationPermission() || isUpdating) return
        isUpdating = true

        fetchLastKnownLocation()

        // 1. Request Fused Location Updates
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2000L)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { updateLocation(it) }
                }
            }
            locationCallback = callback
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            Log.d(TAG, "Started fused location updates")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting fused location updates", e)
        }

        // 2. Request Native Location Manager Updates as backup
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    updateLocation(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            nativeLocationListener = listener
            locationManager?.let { mgr ->
                if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 1.0f, listener, Looper.getMainLooper())
                }
                if (mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 1.0f, listener, Looper.getMainLooper())
                }
            }
            Log.d(TAG, "Started native location listener fallback")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting native location listener", e)
        }
    }

    override fun stopLocationUpdates() {
        isUpdating = false
        locationCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing fused location callback", e)
            }
            locationCallback = null
        }
        nativeLocationListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing native location listener", e)
            }
            nativeLocationListener = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentFix(): LocationCoordinates? {
        if (!hasLocationPermission()) return _coordinates.value

        return try {
            val tokenSource = CancellationTokenSource()
            val loc = suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
            if (loc != null) {
                updateLocation(loc)
                _coordinates.value
            } else {
                fetchLastKnownLocation()
                _coordinates.value
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentFix error: ${e.message}")
            fetchLastKnownLocation()
            _coordinates.value
        }
    }
}
