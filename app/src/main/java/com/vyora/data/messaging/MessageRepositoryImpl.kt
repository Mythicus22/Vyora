package com.vyora.data.messaging

import com.vyora.communication.MessageTransport
import com.vyora.domain.model.Conversation
import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import com.vyora.domain.model.MessageType
import com.vyora.domain.model.SosAlert
import com.vyora.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

import com.vyora.communication.radio.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageRepositoryImpl(
    private val transport: MessageTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : MessageRepository {

    init {
        if (transport is UsbSerialTransport) {
            scope.launch {
                transport.receivedMessages.collect { incoming ->
                    receiveIncomingMessage(incoming)
                }
            }
            scope.launch {
                transport.messageStatusUpdates.collect { (msgId, status) ->
                    updateMessageStatus(msgId, status)
                }
            }
        }
    }

    private fun updateMessageStatus(appMsgId: String, newStatus: MessageDeliveryStatus) {
        val allMsgs = _messages.value.toMutableMap()
        var updated = false
        for ((peer, list) in allMsgs) {
            val idx = list.indexOfFirst { it.id == appMsgId }
            if (idx >= 0) {
                val updatedList = list.toMutableList()
                updatedList[idx] = updatedList[idx].copy(status = newStatus)
                allMsgs[peer] = updatedList
                updated = true
                break
            }
        }
        if (updated) {
            _messages.value = allMsgs
        }
    }

    private fun receiveIncomingMessage(incoming: Message) {
        val targetPeer = incoming.senderId
        val currentList = _messages.value[targetPeer]?.toMutableList() ?: mutableListOf()
        currentList.add(incoming)
        _messages.value = _messages.value.toMutableMap().apply { put(targetPeer, currentList) }

        // If incoming packet is an emergency broadcast, alert this listening node immediately
        if (incoming.type == MessageType.EMERGENCY_ALERT || incoming.text.startsWith("SOS:")) {
            val parts = incoming.text.split(":")
            val cat = if (parts.size > 1) {
                try { EmergencyCategory.valueOf(parts[1]) } catch (e: Exception) { EmergencyCategory.GENERAL }
            } else EmergencyCategory.GENERAL
            val senderNick = if (parts.size > 2 && parts[2].isNotEmpty()) parts[2] else targetPeer
            val lat = if (parts.size > 3) parts[3].toDoubleOrNull() else null
            val lng = if (parts.size > 4) parts[4].toDoubleOrNull() else null
            val note = if (parts.size > 5 && parts[5].isNotEmpty()) parts[5] else "Emergency broadcast received from $senderNick"

            val incomingAlert = SosAlert(
                alertId = incoming.id,
                senderId = targetPeer,
                senderNickname = senderNick,
                category = cat,
                latitude = lat,
                longitude = lng,
                note = note,
                isSafe = false
            )
            _activeSos.value = incomingAlert
        }

        val convList = _conversations.value.toMutableList()
        val existingIndex = convList.indexOfFirst { it.recipientId == targetPeer }
        val updatedConv = Conversation(
            id = if (existingIndex >= 0) convList[existingIndex].id else UUID.randomUUID().toString(),
            recipientId = targetPeer,
            recipientNickname = if (existingIndex >= 0) convList[existingIndex].recipientNickname else "Remote Radio Node",
            lastMessageText = incoming.text,
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = (if (existingIndex >= 0) convList[existingIndex].unreadCount else 0) + 1
        )
        if (existingIndex >= 0) convList[existingIndex] = updatedConv else convList.add(0, updatedConv)
        _conversations.value = convList
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    override val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _activeSos = MutableStateFlow<SosAlert?>(null)
    override val activeSos: StateFlow<SosAlert?> = _activeSos.asStateFlow()

    override suspend fun sendMessage(
        myUserId: String,
        recipientId: String,
        text: String,
        type: MessageType
    ): Message {
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = recipientId,
            senderId = myUserId,
            recipientId = recipientId,
            text = text,
            status = MessageDeliveryStatus.QUEUED_LOCALLY,
            type = type,
            isFromMe = true
        )

        val currentList = _messages.value[recipientId]?.toMutableList() ?: mutableListOf()
        currentList.add(message)
        _messages.value = _messages.value.toMutableMap().apply { put(recipientId, currentList) }

        val convList = _conversations.value.toMutableList()
        val existingIndex = convList.indexOfFirst { it.recipientId == recipientId }
        val updatedConv = Conversation(
            id = if (existingIndex >= 0) convList[existingIndex].id else UUID.randomUUID().toString(),
            recipientId = recipientId,
            recipientNickname = if (existingIndex >= 0) convList[existingIndex].recipientNickname else "Peer $recipientId",
            lastMessageText = text,
            lastMessageTimestamp = System.currentTimeMillis()
        )
        if (existingIndex >= 0) convList[existingIndex] = updatedConv else convList.add(0, updatedConv)
        _conversations.value = convList

        val resultStatus = transport.transmit(message)
        val finalMessage = message.copy(status = resultStatus)

        val updatedList = _messages.value[recipientId]?.toMutableList() ?: mutableListOf()
        val idx = updatedList.indexOfFirst { it.id == message.id }
        if (idx >= 0) {
            updatedList[idx] = finalMessage
            _messages.value = _messages.value.toMutableMap().apply { put(recipientId, updatedList) }
        }

        return finalMessage
    }

    override suspend fun triggerSos(
        myUserId: String,
        myNickname: String,
        category: EmergencyCategory,
        lat: Double?,
        lng: Double?,
        note: String
    ): SosAlert {
        val alert = SosAlert(
            alertId = "SOS-${UUID.randomUUID().toString().take(8).uppercase()}",
            senderId = myUserId,
            senderNickname = myNickname,
            category = category,
            latitude = lat,
            longitude = lng,
            note = note,
            isSafe = false
        )
        _activeSos.value = alert
        transport.broadcastSos(alert)
        return alert
    }

    override suspend fun resolveSos() {
        val current = _activeSos.value ?: return
        val resolved = current.copy(isSafe = true)
        _activeSos.value = null
        transport.broadcastSos(resolved)
    }
}
