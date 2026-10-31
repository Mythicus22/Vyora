package com.vyora.domain.repository

import com.vyora.ai.LocalInferenceEngine
import com.vyora.ai.ModelDownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class EmergencyGuidance(
    val title: String,
    val category: String,
    val summary: String,
    val steps: List<String>,
    val warning: String = ""
)

interface AssistantRepository {
    val downloadState: StateFlow<ModelDownloadState>
    val engineState: StateFlow<LocalInferenceEngine.EngineState>
    val isModelInstalled: Boolean

    fun startModelDownload()
    fun cancelModelDownload()
    fun deleteModel(): Boolean

    suspend fun loadModel(): Result<Unit>
    suspend fun unloadModel()
    fun cancelGeneration()

    fun streamGuidance(query: String): Flow<String>
    suspend fun queryGuidance(query: String): EmergencyGuidance
    fun getQuickProtocols(): List<EmergencyGuidance>
}
