package com.vyora.core.location

import kotlinx.coroutines.flow.StateFlow

data class LocationCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

interface LocationProvider {
    val coordinates: StateFlow<LocationCoordinates?>
    suspend fun getCurrentFix(): LocationCoordinates?
}
