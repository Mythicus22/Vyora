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
    private val createIdentityUseCase: CreateIdentityUseCase = CreateIdentityUseCase(identityRepository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(generatedUserId = createIdentityUseCase.generateUniqueUserId())
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            identityRepository.identityFlow.collect { identity ->
                if (identity != null) {
                    _uiState.update { it.copy(existingIdentity = identity, isCompleted = true) }
                }
            }
        }
    }

    fun onNicknameChanged(newNickname: String) {
        _uiState.update { it.copy(nickname = newNickname) }
    }

    fun regenerateUserId() {
        _uiState.update { it.copy(generatedUserId = createIdentityUseCase.generateUniqueUserId()) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        val current = _uiState.value
        val nick = current.nickname.trim().ifEmpty { "Node-${current.generatedUserId.takeLast(4)}" }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            createIdentityUseCase(nickname = nick, userId = current.generatedUserId)
            _uiState.update { it.copy(isSaving = false, isCompleted = true) }
            onSuccess()
        }
    }
}
