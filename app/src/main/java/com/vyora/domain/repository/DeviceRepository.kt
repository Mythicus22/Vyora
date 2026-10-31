package com.vyora.domain.repository

import com.vyora.domain.model.NearbyDevice
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val nearbyDevices: StateFlow<List<NearbyDevice>>
    suspend fun refreshDevices()
}
