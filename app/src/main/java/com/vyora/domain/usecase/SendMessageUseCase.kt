package com.vyora.domain.usecase

import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageType
import com.vyora.domain.repository.MessageRepository

class SendMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(
        myUserId: String,
        recipientId: String,
        text: String,
        type: MessageType = MessageType.NORMAL
    ): Message {
        return messageRepository.sendMessage(myUserId, recipientId, text, type)
    }
}
