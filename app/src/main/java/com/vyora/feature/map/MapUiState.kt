package com.vyora.feature.map

import com.vyora.core.location.LocationCoordinates
import com.vyora.domain.model.NearbyDevice

data class MapUiState(
    val myCoordinates: LocationCoordinates? = null,
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val selectedDevice: NearbyDevice? = null,
    val isGpsSearching: Boolean = false,
    val directAddressQuery: String = ""
)
