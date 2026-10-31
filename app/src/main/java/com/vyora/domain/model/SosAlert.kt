package com.vyora.domain.model

enum class EmergencyCategory(val label: String, val code: String) {
    GENERAL("General Emergency", "GEN"),
    MEDICAL("Medical / First Aid", "MED"),
    FIRE("Fire / Evacuation", "FIR"),
    FLOOD("Flood / Rising Water", "FLD"),
    EARTHQUAKE("Earthquake / Structural", "EQK"),
    TRAPPED("Trapped / Search & Rescue", "TRP")
}

data class SosAlert(
    val alertId: String,
    val senderId: String,
    val senderNickname: String,
    val category: EmergencyCategory,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String = "",
    val isSafe: Boolean = false
)
