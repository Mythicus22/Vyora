package com.vyora.communication

import com.vyora.domain.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.StateFlow

interface DeliveryTracker {
    val pendingAcks: StateFlow<Set<String>>
    fun trackMessage(messageId: String)
    fun acknowledgeDelivery(messageId: String): MessageDeliveryStatus
    fun markExpired(messageId: String)
}
