package com.vyora.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.core.location.LocationProvider
import com.vyora.core.location.OfflineMapTileManager
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val locationProvider: LocationProvider,
    private val deviceRepository: DeviceRepository,
    private val offlineMapTileManager: OfflineMapTileManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            locationProvider.coordinates.collect { coords ->
                _uiState.update { it.copy(myCoordinates = coords) }
            }
        }
        viewModelScope.launch {
            deviceRepository.nearbyDevices.collect { devices ->
                _uiState.update { it.copy(nearbyDevices = devices) }
            }
        }
        offlineMapTileManager?.let { tileMgr ->
            viewModelScope.launch {
                tileMgr.downloadProgress.collect { progress ->
                    _uiState.update { it.copy(offlineProgress = progress) }
                }
            }
        }
    }

    fun selectDevice(device: NearbyDevice?) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun setFilter(filter: MapFilterCategory) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun toggleRadarOverlay() {
        _uiState.update { it.copy(isRadarOverlayVisible = !it.isRadarOverlayVisible) }
    }

    fun toggleSatelliteView() {
        _uiState.update { it.copy(isSatelliteView = !it.isSatelliteView) }
    }

    fun cacheCurrentRegion(styleUrl: String) {
        val coords = _uiState.value.myCoordinates ?: return
        offlineMapTileManager?.cacheRegionAround(
            centerLat = coords.latitude,
            centerLng = coords.longitude,
            styleUrl = styleUrl
        )
    }

    fun recenterGps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGpsSearching = true) }
            locationProvider.getCurrentFix()
            _uiState.update { it.copy(isGpsSearching = false) }
        }
    }
}
