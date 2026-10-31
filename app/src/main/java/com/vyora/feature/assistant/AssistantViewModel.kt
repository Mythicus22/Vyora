package com.vyora.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.ai.LocalInferenceEngine
import com.vyora.ai.ModelDownloadState
import com.vyora.domain.repository.AssistantRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            quickProtocols = assistantRepository.getQuickProtocols(),
            chatHistory = listOf(
                AssistantMessageItem(
                    id = "init",
                    text = "VYORA SAFORA AI Assistant (Qwen2 2B fine-tuned). Runs completely offline on-device with deterministic local emergency knowledge.",
                    isUser = false
                )
            )
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        // Observe download state
        viewModelScope.launch {
            assistantRepository.downloadState.collect { downloadState ->
                updateLifecycleState(downloadState = downloadState)
            }
        }

        // Observe native engine state
        viewModelScope.launch {
            assistantRepository.engineState.collect { engineState ->
                updateLifecycleState(engineState = engineState)
            }
        }
    }

    private fun updateLifecycleState(
        downloadState: ModelDownloadState = _uiState.value.downloadProgress,
        engineState: LocalInferenceEngine.EngineState = _uiState.value.engineState
    ) {
        val isInstalled = assistantRepository.isModelInstalled
        val isLoaded = engineState is LocalInferenceEngine.EngineState.Ready ||
                engineState is LocalInferenceEngine.EngineState.Generating
        val isGenerating = engineState is LocalInferenceEngine.EngineState.Generating

        val (lifecycle, statusMsg) = when {
            downloadState is ModelDownloadState.Downloading -> {
                ModelLifecycleStatus.DOWNLOADING to "Downloading SAFORA GGUF: ${downloadState.percent}% (${downloadState.downloadedBytes / (1024 * 1024)}MB / ${downloadState.totalBytes / (1024 * 1024)}MB)"
            }
            downloadState is ModelDownloadState.Validating -> {
                ModelLifecycleStatus.DOWNLOADING to "Validating model checksum (SHA-256)..."
            }
            downloadState is ModelDownloadState.Failed -> {
                ModelLifecycleStatus.DOWNLOAD_FAILED to "Download failed: ${downloadState.reason}"
            }
            engineState is LocalInferenceEngine.EngineState.Loading -> {
                ModelLifecycleStatus.LOADING to "Loading SAFORA into memory (llama.cpp)..."
            }
            engineState is LocalInferenceEngine.EngineState.Generating -> {
                ModelLifecycleStatus.GENERATING to "SAFORA generating emergency response..."
            }
            engineState is LocalInferenceEngine.EngineState.Ready -> {
                ModelLifecycleStatus.READY to "SAFORA Ready (Offline On-Device AI Active)"
            }
            engineState is LocalInferenceEngine.EngineState.Error -> {
                ModelLifecycleStatus.ERROR to "Engine error: ${engineState.message}"
            }
            isInstalled -> {
                ModelLifecycleStatus.INSTALLED_UNLOADED to "SAFORA Installed (Offline). Ready to load."
            }
            else -> {
                ModelLifecycleStatus.NOT_DOWNLOADED to "SAFORA Model Not Downloaded (~986 MB required for full offline neural AI)"
            }
        }

        _uiState.update { current ->
            current.copy(
                lifecycleStatus = lifecycle,
                statusMessage = statusMsg,
                downloadProgress = downloadState,
                engineState = engineState,
                isModelInstalled = isInstalled,
                isModelLoaded = isLoaded,
                isGenerating = isGenerating
            )
        }
    }

    fun startModelDownload() {
        assistantRepository.startModelDownload()
    }

    fun cancelModelDownload() {
        assistantRepository.cancelModelDownload()
    }

    fun deleteModel() {
        if (assistantRepository.deleteModel()) {
            _uiState.update {
                it.copy(
                    lifecycleStatus = ModelLifecycleStatus.NOT_DOWNLOADED,
                    statusMessage = "SAFORA model deleted. Storage freed."
                )
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            val result = assistantRepository.loadModel()
            result.onFailure { t ->
                _uiState.update {
                    it.copy(
                        lifecycleStatus = ModelLifecycleStatus.ERROR,
                        errorMessage = t.message
                    )
                }
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            assistantRepository.unloadModel()
        }
    }

    fun cancelGeneration() {
        assistantRepository.cancelGeneration()
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { current ->
            current.copy(
                isGenerating = false,
                chatHistory = current.chatHistory.map {
                    if (it.isStreaming) it.copy(isStreaming = false, text = it.text + " [Stopped]") else it
                }
            )
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendQuery(prompt: String = _uiState.value.inputText) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val userMsgId = "user_${System.currentTimeMillis()}"
        val assistantMsgId = "assistant_${System.currentTimeMillis() + 1}"

        val userMsg = AssistantMessageItem(id = userMsgId, text = clean, isUser = true)
        val initialAssistantMsg = AssistantMessageItem(
            id = assistantMsgId,
            text = "",
            isUser = false,
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                inputText = "",
                chatHistory = it.chatHistory + userMsg + initialAssistantMsg
            )
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val responseBuilder = StringBuilder()

            assistantRepository.streamGuidance(clean)
                .catch { error ->
                    val errorText = "\n[Error: ${error.localizedMessage ?: "Generation interrupted"}]"
                    responseBuilder.append(errorText)
                    _uiState.update { state ->
                        state.copy(
                            chatHistory = state.chatHistory.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = responseBuilder.toString(), isStreaming = false)
                                } else msg
                            }
                        )
                    }
                }
                .collect { token ->
                    responseBuilder.append(token)
                    _uiState.update { state ->
                        state.copy(
                            chatHistory = state.chatHistory.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = responseBuilder.toString())
                                } else msg
                            }
                        )
                    }
                }

            _uiState.update { state ->
                state.copy(
                    chatHistory = state.chatHistory.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(isStreaming = false)
                        } else msg
                    }
                )
            }
        }
    }
}
