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

    private val _conversations = MutableStateFlow<List<Conversation>>(
        listOf(
            Conversation(
                id = "conv-1",
                recipientId = "VYO-8K42-99P1",
                recipientNickname = "Relay-Bravo",
                lastMessageText = "Sub-GHz 915MHz active beacon verified.",
                lastMessageTimestamp = System.currentTimeMillis() - 120_000
            ),
            Conversation(
                id = "conv-2",
                recipientId = "VYO-3X99-2A1B",
                recipientNickname = "FieldMedic-Sarah",
                lastMessageText = "First aid supply station established at Sector 4.",
                lastMessageTimestamp = System.currentTimeMillis() - 600_000,
                unreadCount = 1
            )
        )
    )
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(
        mapOf(
            "VYO-8K42-99P1" to listOf(
                Message(
                    id = "m1",
                    conversationId = "conv-1",
                    senderId = "VYO-8K42-99P1",
                    recipientId = "ME",
                    text = "Sub-GHz 915MHz active beacon verified.",
                    status = MessageDeliveryStatus.DELIVERED,
                    isFromMe = false
                )
            ),
            "VYO-3X99-2A1B" to listOf(
                Message(
                    id = "m2",
                    conversationId = "conv-2",
                    senderId = "VYO-3X99-2A1B",
                    recipientId = "ME",
                    text = "First aid supply station established at Sector 4.",
                    status = MessageDeliveryStatus.DELIVERED,
                    isFromMe = false
                )
            )
        )
    )
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
