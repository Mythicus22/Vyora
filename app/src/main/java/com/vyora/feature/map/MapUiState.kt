package com.vyora.feature.map

import com.vyora.core.location.LocationCoordinates
import com.vyora.core.location.OfflineMapTileManager
import com.vyora.domain.model.NearbyDevice

enum class MapFilterCategory(val label: String) {
    ALL("All Nodes"),
    SOS("SOS Alerts"),
    MEDICAL("Medical"),
    RELAY("Relays")
}

data class MapUiState(
    val myCoordinates: LocationCoordinates? = null,
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val selectedDevice: NearbyDevice? = null,
    val isGpsSearching: Boolean = false,
    val activeFilter: MapFilterCategory = MapFilterCategory.ALL,
    val isRadarOverlayVisible: Boolean = true,
    val isSatelliteView: Boolean = false,
    val offlineProgress: OfflineMapTileManager.DownloadProgress = OfflineMapTileManager.DownloadProgress()
) {
    val filteredDevices: List<NearbyDevice>
        get() = when (activeFilter) {
            MapFilterCategory.ALL -> nearbyDevices
            MapFilterCategory.SOS -> nearbyDevices.filter { it.nickname.contains("SOS", ignoreCase = true) || it.nickname.contains("Rescue", ignoreCase = true) }
            MapFilterCategory.MEDICAL -> nearbyDevices.filter { it.nickname.contains("Medic", ignoreCase = true) }
            MapFilterCategory.RELAY -> nearbyDevices.filter { it.nickname.contains("Relay", ignoreCase = true) || it.nickname.contains("Outpost", ignoreCase = true) }
        }
}
