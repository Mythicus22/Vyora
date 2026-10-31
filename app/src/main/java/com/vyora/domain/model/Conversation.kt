package com.vyora.domain.model

data class Conversation(
    val id: String,
    val recipientId: String,
    val recipientNickname: String,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isEmergencyActive: Boolean = false
)
