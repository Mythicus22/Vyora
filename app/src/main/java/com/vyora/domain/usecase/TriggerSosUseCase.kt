package com.vyora.domain.usecase

import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.model.SosAlert
import com.vyora.domain.repository.MessageRepository

class TriggerSosUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(
        myUserId: String,
        myNickname: String,
        category: EmergencyCategory,
        lat: Double?,
        lng: Double?,
        note: String
    ): SosAlert {
        return messageRepository.triggerSos(myUserId, myNickname, category, lat, lng, note)
    }

    suspend fun resolve() {
        messageRepository.resolveSos()
    }
}
