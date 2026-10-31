package com.vyora.communication

import com.vyora.domain.model.Message
import kotlinx.coroutines.flow.StateFlow

data class RouteInfo(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val rssi: Int
)

interface MessageRouter {
    val routingTable: StateFlow<Map<String, RouteInfo>>
    fun routeMessage(message: Message): Boolean
    fun handleIncomingFrame(frame: ByteArray): Message?
}
