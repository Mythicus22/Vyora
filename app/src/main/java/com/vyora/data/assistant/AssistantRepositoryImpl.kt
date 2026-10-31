package com.vyora.data.assistant

import android.content.Context
import com.vyora.ai.EmergencyKnowledgeRepository
import com.vyora.ai.LocalInferenceEngine
import com.vyora.ai.LocalModelManager
import com.vyora.ai.ModelDownloadState
import com.vyora.ai.SaforaPromptFormatter
import com.vyora.domain.repository.AssistantRepository
import com.vyora.domain.repository.EmergencyGuidance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class AssistantRepositoryImpl(
    private val context: Context,
    private val knowledgeRepo: EmergencyKnowledgeRepository,
    private val modelManager: LocalModelManager = LocalModelManager(context),
    private val inferenceEngine: LocalInferenceEngine = LocalInferenceEngine.getInstance()
) : AssistantRepository {

    override val downloadState: StateFlow<ModelDownloadState> = modelManager.downloadState
    override val engineState: StateFlow<LocalInferenceEngine.EngineState> = inferenceEngine.engineState

    override val isModelInstalled: Boolean
        get() = modelManager.isModelInstalled()

    override fun startModelDownload() {
        modelManager.startDownload()
    }

    override fun cancelModelDownload() {
        modelManager.cancelDownload()
    }

    override fun deleteModel(): Boolean {
        // If loaded, unload first
        return modelManager.deleteModel()
    }

    override suspend fun loadModel(): Result<Unit> {
        val modelFile = modelManager.getModelFile()
        return inferenceEngine.loadModel(context, modelFile)
    }

    override suspend fun unloadModel() {
        inferenceEngine.unloadModel()
    }

    override fun cancelGeneration() {
        inferenceEngine.cancelGeneration()
    }

    override fun streamGuidance(query: String): Flow<String> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return flow { }
        }

        if (inferenceEngine.isModelReady()) {
            // Find relevant local emergency protocols to inject into RAG prompt
            val relevantProtocols = findRelevantProtocols(cleanQuery)
            val systemPrompt = SaforaPromptFormatter.buildSystemPrompt(relevantProtocols)
            val formattedPrompt = SaforaPromptFormatter.formatChatPrompt(
                history = emptyList<Pair<String, String>>(),
                currentMessage = cleanQuery,
                emergencyProtocols = relevantProtocols
            )
            return inferenceEngine.generate(
                userPrompt = formattedPrompt,
                maxTokens = 512,
                systemPrompt = systemPrompt
            )
        } else {
            // Model not loaded: fallback to deterministic local protocol with clear status message
            return flow {
                val guidance = queryGuidance(cleanQuery)
                val statusPrefix = if (isModelInstalled) {
                    "[Offline Notice: SAFORA neural model installed but not loaded. Tap 'Load Model' above for deep neural generation. Showing verified emergency protocol:]\n\n"
                } else {
                    "[Offline Notice: SAFORA AI model not yet downloaded (~986 MB). Tap 'Download SAFORA' above for offline AI. Showing verified emergency protocol:]\n\n"
                }
                emit(statusPrefix)
                emit("**${guidance.title}** (${guidance.category})\n")
                emit("${guidance.summary}\n\n")
                if (guidance.steps.isNotEmpty()) {
                    emit("ACTION STEPS:\n")
                    guidance.steps.forEachIndexed { idx, step ->
                        emit("${idx + 1}. $step\n")
                    }
                }
                if (guidance.warning.isNotEmpty()) {
                    emit("\nWARNING: ${guidance.warning}\n")
                }
            }
        }
    }

    override suspend fun queryGuidance(query: String): EmergencyGuidance {
        val protocols = knowledgeRepo.getProtocols()
        val q = query.lowercase()
        return protocols.find { p ->
            p.category.lowercase() in q || p.title.lowercase().split(" ").any { it.length > 3 && it in q }
        } ?: protocols.first()
    }

    override fun getQuickProtocols(): List<EmergencyGuidance> = knowledgeRepo.getProtocols()

    private fun findRelevantProtocols(query: String): List<EmergencyGuidance> {
        val protocols = knowledgeRepo.getProtocols()
        val q = query.lowercase()
        val matched = protocols.filter { p ->
            p.category.lowercase() in q ||
                    p.title.lowercase().split(" ").any { it.length > 3 && it in q } ||
                    p.summary.lowercase().split(" ").any { it.length > 4 && it in q }
        }
        return matched.ifEmpty { emptyList() }
    }
}
