package com.vyora.feature.onboarding

import com.vyora.domain.model.UserIdentity

data class OnboardingUiState(
    val generatedUserId: String = "",
    val nickname: String = "",
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val existingIdentity: UserIdentity? = null
)
