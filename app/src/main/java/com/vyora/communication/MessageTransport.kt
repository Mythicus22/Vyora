package com.vyora.communication

import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.model.SosAlert
import kotlinx.coroutines.flow.StateFlow

enum class TransportState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

interface MessageTransport {
    val state: StateFlow<TransportState>
    val discoveredDevices: StateFlow<List<NearbyDevice>>
    val transportName: String

    suspend fun start()
    suspend fun stop()
    suspend fun transmit(message: Message): MessageDeliveryStatus
    suspend fun broadcastSos(alert: SosAlert): Boolean
}
