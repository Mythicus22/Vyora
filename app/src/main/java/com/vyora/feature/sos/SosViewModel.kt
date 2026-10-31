package com.vyora.feature.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.repository.MessageRepository
import com.vyora.domain.usecase.TriggerSosUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SosViewModel(
    private val messageRepository: MessageRepository,
    private val triggerSosUseCase: TriggerSosUseCase = TriggerSosUseCase(messageRepository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            messageRepository.activeSos.collect { alert ->
                _uiState.update { it.copy(activeAlert = alert) }
            }
        }
    }

    fun selectCategory(category: EmergencyCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun startCountdown(myUserId: String, myNickname: String, lat: Double?, lng: Double?) {
        _uiState.update { it.copy(countdownSeconds = 5) }
        viewModelScope.launch {
            while ((_uiState.value.countdownSeconds ?: 0) > 0) {
                delay(1000)
                _uiState.update { it.copy(countdownSeconds = (it.countdownSeconds ?: 1) - 1) }
            }
            if (_uiState.value.countdownSeconds == 0) {
                _uiState.update { it.copy(countdownSeconds = null, isTransmitting = true) }
                triggerSosUseCase(
                    myUserId = myUserId,
                    myNickname = myNickname,
                    category = _uiState.value.selectedCategory,
                    lat = lat,
                    lng = lng,
                    note = _uiState.value.note
                )
                _uiState.update { it.copy(isTransmitting = false) }
            }
        }
    }

    fun cancelCountdown() {
        _uiState.update { it.copy(countdownSeconds = null) }
    }

    fun resolveSos() {
        viewModelScope.launch {
            triggerSosUseCase.resolve()
        }
    }
}
