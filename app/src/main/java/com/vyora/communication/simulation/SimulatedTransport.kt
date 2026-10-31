package com.vyora.communication.simulation

import com.vyora.communication.MessageTransport
import com.vyora.communication.TransportState
import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.model.ReachabilityStatus
import com.vyora.domain.model.SosAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimulatedTransport : MessageTransport {

    private val _state = MutableStateFlow(TransportState.CONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow(
        listOf(
            NearbyDevice(
                deviceId = "VYO-8K42-99P1",
                nickname = "Relay-Bravo",
                reachability = ReachabilityStatus.REACHABLE_DIRECT,
                latitude = 37.7760,
                longitude = -122.4170,
                rssi = -68,
                hopCount = 1,
                isSimulated = true
            ),
            NearbyDevice(
                deviceId = "VYO-3X99-2A1B",
                nickname = "FieldMedic-Sarah",
                reachability = ReachabilityStatus.REACHABLE_DIRECT,
                latitude = 37.7735,
                longitude = -122.4215,
                rssi = -82,
                hopCount = 1,
                isSimulated = true
            ),
            NearbyDevice(
                deviceId = "VYO-7M14-K008",
                nickname = "Outpost-Echo",
                reachability = ReachabilityStatus.REACHABLE_RELAY,
                latitude = 37.7785,
                longitude = -122.4140,
                rssi = -91,
                hopCount = 2,
                isSimulated = true
            )
        )
    )
    override val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    override val transportName: String = "DEV-SIMULATED-MESH"

    override suspend fun start() {
        _state.value = TransportState.CONNECTED
    }

    override suspend fun stop() {
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun transmit(message: Message): MessageDeliveryStatus {
        val target = _discoveredDevices.value.find { it.deviceId == message.recipientId }
        return when (target?.reachability) {
            ReachabilityStatus.REACHABLE_DIRECT -> MessageDeliveryStatus.DELIVERED
            ReachabilityStatus.REACHABLE_RELAY -> MessageDeliveryStatus.SENT_TO_RELAY
            else -> MessageDeliveryStatus.WAITING_FOR_ROUTE
        }
    }

    override suspend fun broadcastSos(alert: SosAlert): Boolean = true
}
