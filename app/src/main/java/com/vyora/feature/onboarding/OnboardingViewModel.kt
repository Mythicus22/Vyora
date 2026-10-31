package com.vyora.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.domain.repository.IdentityRepository
import com.vyora.domain.usecase.CreateIdentityUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val identityRepository: IdentityRepository,
    private val transport: com.vyora.communication.MessageTransport? = null,
    private val createIdentityUseCase: CreateIdentityUseCase = CreateIdentityUseCase(identityRepository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val storedId = identityRepository.getStoredUserId()
            if (!storedId.isNullOrEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        generatedUserId = storedId,
                        hasStoredId = true,
                        isIdReadFromEsp = true,
                        espNodeId = CreateIdentityUseCase.extractNodeId(storedId)
                    )
                }
            }
            identityRepository.identityFlow.collect { identity ->
                if (identity != null) {
                    _uiState.update { current ->
                        current.copy(
                            existingIdentity = identity,
                            generatedUserId = identity.userId.ifEmpty { current.generatedUserId },
                            hasStoredId = identity.userId.isNotEmpty(),
                            isIdReadFromEsp = identity.userId.isNotEmpty() || current.isIdReadFromEsp,
                            nickname = if (current.nickname.isEmpty()) identity.nickname else current.nickname
                        )
                    }
                }
            }
        }

        // Listen for hardware node ID and connection state from connected ESP32 serial
        if (transport is com.vyora.communication.radio.UsbSerialTransport) {
            viewModelScope.launch {
                transport.state.collect { state ->
                    val isConnected = state == com.vyora.communication.TransportState.CONNECTED
                    _uiState.update { it.copy(isEspConnected = isConnected) }
                    if (isConnected) {
                        transport.queryStatus()
                    }
                }
            }

            viewModelScope.launch {
                transport.localMacAddress.collect { mac ->
                    if (!mac.isNullOrEmpty()) {
                        _uiState.update { it.copy(espMacAddress = mac) }
                    }
                }
            }

            viewModelScope.launch {
                transport.localNodeId.collect { espId ->
                    if (!espId.isNullOrEmpty() && espId != "00000000") {
                        val formatted = "VYO-${espId.uppercase()}"
                        identityRepository.savePermanentNodeId(espId)
                        _uiState.update {
                            it.copy(
                                generatedUserId = formatted,
                                isIdReadFromEsp = true,
                                hasStoredId = true,
                                espNodeId = espId.uppercase()
                            )
                        }
                    }
                }
            }
            // Check if transport already has node ID or MAC resolved
            transport.localNodeId.value?.let { currentId ->
                if (currentId.isNotEmpty() && currentId != "00000000") {
                    val formatted = "VYO-${currentId.uppercase()}"
                    _uiState.update {
                        it.copy(
                            generatedUserId = formatted,
                            isIdReadFromEsp = true,
                            hasStoredId = true,
                            espNodeId = currentId.uppercase()
                        )
                    }
                }
            }
            transport.localMacAddress.value?.let { mac ->
                if (mac.isNotEmpty()) {
                    _uiState.update { it.copy(espMacAddress = mac) }
                }
            }
            // Proactively query status on initialization
            transport.queryStatus()
        }
    }

    fun queryEspHardwareNodeId() {
        if (transport is com.vyora.communication.radio.UsbSerialTransport) {
            viewModelScope.launch {
                _uiState.update { it.copy(isQueryingSerial = true) }
                if (transport.state.value != com.vyora.communication.TransportState.CONNECTED) {
                    transport.connectAvailableDevice()
                }
                transport.queryStatus()

                val currentEspId = transport.localNodeId.value
                if (!currentEspId.isNullOrEmpty() && currentEspId != "00000000") {
                    val formatted = "VYO-${currentEspId.uppercase()}"
                    identityRepository.savePermanentNodeId(currentEspId)
                    _uiState.update {
                        it.copy(
                            generatedUserId = formatted,
                            isIdReadFromEsp = true,
                            hasStoredId = true,
                            espNodeId = currentEspId.uppercase()
                        )
                    }
                }
                val currentMac = transport.localMacAddress.value
                if (!currentMac.isNullOrEmpty()) {
                    _uiState.update { it.copy(espMacAddress = currentMac) }
                }

                kotlinx.coroutines.delay(900)
                _uiState.update { it.copy(isQueryingSerial = false) }
            }
        }
    }

    fun onNicknameChanged(newNickname: String) {
        _uiState.update { it.copy(nickname = newNickname) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        val current = _uiState.value
        val finalUserId = current.generatedUserId.ifEmpty {
            current.espNodeId?.let { "VYO-$it" } ?: ""
        }
        if (finalUserId.isEmpty()) {
            return
        }
        val nick = current.nickname.trim().ifEmpty {
            val nodeHex = CreateIdentityUseCase.extractNodeId(finalUserId)
            "Node-${nodeHex.takeLast(4)}"
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            createIdentityUseCase(nickname = nick, userId = finalUserId)
            if (transport is com.vyora.communication.radio.UsbSerialTransport) {
                transport.setUserProfile(userId = finalUserId, callsign = nick)
            }
            _uiState.update { it.copy(isSaving = false, isCompleted = true) }
            onSuccess()
        }
    }
}
