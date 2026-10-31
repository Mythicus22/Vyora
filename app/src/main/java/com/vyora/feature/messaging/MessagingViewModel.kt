package com.vyora.feature.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.domain.model.Message
import com.vyora.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MessagingViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagingUiState())
    val uiState: StateFlow<MessagingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messageRepository.conversations.collect { convs ->
                _uiState.update { it.copy(conversations = convs) }
            }
        }
    }

    fun selectConversation(peerId: String) {
        _uiState.update { it.copy(activePeerId = peerId) }
        viewModelScope.launch {
            messageRepository.messages.collect { allMsgs ->
                val thread = allMsgs[peerId] ?: emptyList()
                _uiState.update { it.copy(currentMessages = thread) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(myUserId: String, peerId: String) {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            messageRepository.sendMessage(
                myUserId = myUserId,
                recipientId = peerId,
                text = text
            )
        }
    }
}
