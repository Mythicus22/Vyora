package com.vyora.feature.sos

import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.model.SosAlert

data class SosUiState(
    val activeAlert: SosAlert? = null,
    val selectedCategory: EmergencyCategory = EmergencyCategory.GENERAL,
    val note: String = "",
    val countdownSeconds: Int? = null,
    val isTransmitting: Boolean = false
)
