package com.vyora.feature.assistant

import com.vyora.ai.LocalInferenceEngine
import com.vyora.ai.ModelDownloadState
import com.vyora.domain.repository.EmergencyGuidance

enum class ModelLifecycleStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    INSTALLED_UNLOADED,
    LOADING,
    READY,
    GENERATING,
    ERROR
}

data class AssistantUiState(
    val statusMessage: String = "",
    val lifecycleStatus: ModelLifecycleStatus = ModelLifecycleStatus.NOT_DOWNLOADED,
    val downloadProgress: ModelDownloadState = ModelDownloadState.Idle,
    val engineState: LocalInferenceEngine.EngineState = LocalInferenceEngine.EngineState.Unloaded,
    val isModelInstalled: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val quickProtocols: List<EmergencyGuidance> = emptyList(),
    val chatHistory: List<AssistantMessageItem> = emptyList(),
    val inputText: String = ""
)

data class AssistantMessageItem(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val guidance: EmergencyGuidance? = null
)
