package com.vyora.domain.usecase

import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import com.vyora.domain.repository.MessageRepository

class QueueMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(message: Message): Message {
        return message.copy(status = MessageDeliveryStatus.QUEUED_LOCALLY)
    }
}
