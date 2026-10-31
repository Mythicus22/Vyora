package com.vyora.domain.usecase

import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveNearbyDevicesUseCase(private val deviceRepository: DeviceRepository) {
    operator fun invoke(): StateFlow<List<NearbyDevice>> = deviceRepository.nearbyDevices
}
