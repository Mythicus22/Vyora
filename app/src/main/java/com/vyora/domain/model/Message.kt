package com.vyora.domain.model

enum class MessageDeliveryStatus {
    DRAFT,
    QUEUED_LOCALLY,
    WAITING_FOR_ROUTE,
    SENT_TO_RELAY,
    DELIVERED,
    FAILED_EXPIRED
}

enum class MessageType {
    NORMAL,
    EMERGENCY_ALERT,
    LOCATION_PING,
    SYSTEM_ACK
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageDeliveryStatus = MessageDeliveryStatus.QUEUED_LOCALLY,
    val type: MessageType = MessageType.NORMAL,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFromMe: Boolean = true
)
