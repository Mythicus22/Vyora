package com.vyora.data.emergencyguides

import com.vyora.ai.EmergencyKnowledgeRepository
import com.vyora.domain.repository.EmergencyGuidance

class EmergencyGuidesRepository(private val knowledgeRepo: EmergencyKnowledgeRepository) {
    fun getAllGuides(): List<EmergencyGuidance> = knowledgeRepo.getProtocols()
}
