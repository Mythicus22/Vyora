package com.vyora.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val timestamp: Long,
    val status: String,
    val type: String,
    val latitude: Double?,
    val longitude: Double?,
    val isFromMe: Boolean
)
