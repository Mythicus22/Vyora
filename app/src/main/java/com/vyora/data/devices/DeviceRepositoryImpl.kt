package com.vyora.data.devices

import com.vyora.communication.MessageTransport
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.StateFlow

class DeviceRepositoryImpl(private val transport: MessageTransport) : DeviceRepository {
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = transport.discoveredDevices
    override suspend fun refreshDevices() {}
}
