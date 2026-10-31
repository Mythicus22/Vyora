package com.vyora.data.assistant

import com.vyora.ai.EmergencyKnowledgeRepository
import com.vyora.domain.repository.AssistantRepository
import com.vyora.domain.repository.EmergencyGuidance

class AssistantRepositoryImpl(
    private val knowledgeRepo: EmergencyKnowledgeRepository
) : AssistantRepository {

    override val isModelAvailable: Boolean = false
    override val statusMessage: String = "Offline Neural Runtime Pending: Using Local Emergency Protocols"

    override suspend fun queryGuidance(query: String): EmergencyGuidance {
        val protocols = knowledgeRepo.getProtocols()
        val q = query.lowercase()
        return protocols.find { p ->
            p.category.lowercase() in q || p.title.lowercase().split(" ").any { it.length > 3 && it in q }
        } ?: protocols.first()
    }

    override fun getQuickProtocols(): List<EmergencyGuidance> = knowledgeRepo.getProtocols()
}
