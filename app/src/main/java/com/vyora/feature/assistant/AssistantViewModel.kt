package com.vyora.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.domain.repository.AssistantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            statusMessage = assistantRepository.statusMessage,
            isModelAvailable = assistantRepository.isModelAvailable,
            quickProtocols = assistantRepository.getQuickProtocols(),
            chatHistory = listOf(
                AssistantMessageItem(
                    id = "init",
                    text = "VYORA Local Emergency Assistant loaded. Deterministic triage protocols available offline.",
                    isUser = false
                )
            )
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendQuery(prompt: String = _uiState.value.inputText) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val userMsg = AssistantMessageItem(id = System.currentTimeMillis().toString(), text = clean, isUser = true)
        _uiState.update {
            it.copy(
                inputText = "",
                chatHistory = it.chatHistory + userMsg
            )
        }

        viewModelScope.launch {
            val guidance = assistantRepository.queryGuidance(clean)
            val assistantMsg = AssistantMessageItem(
                id = (System.currentTimeMillis() + 1).toString(),
                text = guidance.summary,
                isUser = false,
                guidance = guidance
            )
            _uiState.update { it.copy(chatHistory = it.chatHistory + assistantMsg) }
        }
    }
}
