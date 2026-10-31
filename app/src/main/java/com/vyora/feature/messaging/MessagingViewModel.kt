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
    private val messageRepository: MessageRepository,
    private val deviceRepository: com.vyora.domain.repository.DeviceRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagingUiState())
    val uiState: StateFlow<MessagingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messageRepository.conversations.collect { convs ->
                _uiState.update { it.copy(conversations = convs) }
            }
        }
        deviceRepository?.let { repo ->
            viewModelScope.launch {
                repo.nearbyDevices.collect { devs ->
                    _uiState.update { it.copy(discoveredDevices = devs) }
                }
            }
        }
    }

    fun isDeviceValid(id: String): Boolean {
        val clean = id.trim().uppercase()
        if (clean.length < 2) return false

        // Matches discovered node or nickname
        val devices = _uiState.value.discoveredDevices
        val matchesDiscovered = devices.any {
            it.deviceId.equals(clean, ignoreCase = true) ||
            it.nickname.equals(clean, ignoreCase = true) ||
            com.vyora.domain.usecase.CreateIdentityUseCase.extractNodeId(it.deviceId).equals(
                com.vyora.domain.usecase.CreateIdentityUseCase.extractNodeId(clean),
                ignoreCase = true
            )
        }
        if (matchesDiscovered) return true

        // Direct 8-hex node ID (e.g. 1A2B3C4D)
        if (clean.matches(Regex("^[0-9A-F]{8}$"))) return true

        // Formatted VYO ID (e.g. VYO-1A2B3C4D)
        if (clean.startsWith("VYO-") && clean.length >= 6) return true

        // MAC address format with colons/dashes or 12 hex chars
        val stripped = clean.replace(":", "").replace("-", "")
        if (stripped.length == 12 && stripped.all { it in "0123456789ABCDEF" }) return true

        // Valid alphanumeric callsign or node identifier
        return clean.length in 2..32 && clean.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' }
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
