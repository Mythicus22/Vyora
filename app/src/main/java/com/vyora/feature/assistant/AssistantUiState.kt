package com.vyora.feature.assistant

import com.vyora.domain.repository.EmergencyGuidance

data class AssistantUiState(
    val statusMessage: String = "",
    val isModelAvailable: Boolean = false,
    val quickProtocols: List<EmergencyGuidance> = emptyList(),
    val chatHistory: List<AssistantMessageItem> = emptyList(),
    val inputText: String = ""
)

data class AssistantMessageItem(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val guidance: EmergencyGuidance? = null
)
