package com.vyora.domain.repository

data class EmergencyGuidance(
    val title: String,
    val category: String,
    val summary: String,
    val steps: List<String>,
    val warning: String = ""
)

interface AssistantRepository {
    val isModelAvailable: Boolean
    val statusMessage: String
    suspend fun queryGuidance(query: String): EmergencyGuidance
    fun getQuickProtocols(): List<EmergencyGuidance>
}
