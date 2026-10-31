package com.vyora.feature.onboarding

import com.vyora.domain.model.UserIdentity

data class OnboardingUiState(
    val generatedUserId: String = "",
    val nickname: String = "",
    val emergencyRole: String = "Responder / General Operator",
    val radioBand: String = "915 MHz US-ISM (nRF24 / Sub-GHz)",
    val emergencyNote: String = "Equipped with offline emergency triage & first aid supplies.",
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val existingIdentity: UserIdentity? = null,
    val hasStoredId: Boolean = false,
    val isEspConnected: Boolean = false,
    val isIdReadFromEsp: Boolean = false,
    val espNodeId: String? = null,
    val espMacAddress: String? = null,
    val isQueryingSerial: Boolean = false
)
