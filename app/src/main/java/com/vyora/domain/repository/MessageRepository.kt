package com.vyora.domain.repository

import com.vyora.domain.model.Conversation
import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageType
import com.vyora.domain.model.SosAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MessageRepository {
    val conversations: StateFlow<List<Conversation>>
    val messages: StateFlow<Map<String, List<Message>>>
    val activeSos: StateFlow<SosAlert?>

    suspend fun sendMessage(
        myUserId: String,
        recipientId: String,
        text: String,
        type: MessageType = MessageType.NORMAL
    ): Message

    suspend fun triggerSos(
        myUserId: String,
        myNickname: String,
        category: EmergencyCategory,
        lat: Double?,
        lng: Double?,
        note: String
    ): SosAlert

    suspend fun resolveSos()
}
