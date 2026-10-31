package com.vyora.ai

import com.vyora.domain.repository.EmergencyGuidance

class EmergencyKnowledgeRepository {
    private val protocols = listOf(
        EmergencyGuidance(
            title = "Severe Bleeding & Hemorrhage",
            category = "First Aid",
            summary = "Direct pressure protocol to control life-threatening blood loss.",
            steps = listOf(
                "Apply continuous firm direct pressure over wound with clean cloth.",
                "Maintain pressure without lifting cloth to check.",
                "If limb bleeding is uncontrolled, apply tourniquet 2-3 inches above wound."
            ),
            warning = "DO NOT loosen a tourniquet once applied."
        ),
        EmergencyGuidance(
            title = "Structure Collapse / Trapped",
            category = "Trapped",
            summary = "Survival guidelines when confined or trapped under debris.",
            steps = listOf(
                "Protect airway: Cover mouth and nose with cloth.",
                "Tap rhythmically on metal pipe or wall (3 taps - pause - 3 taps).",
                "Conserve energy and mobile battery."
            ),
            warning = "DO NOT light matches or lighters due to potential gas leaks."
        ),
        EmergencyGuidance(
            title = "Flash Flood & Rapid Water Rise",
            category = "Flood",
            summary = "Immediate evacuation and high-ground procedures.",
            steps = listOf(
                "Immediately move to the highest accessible ground.",
                "Do NOT enter an attic unless there is roof access.",
                "Never walk, swim, or drive through moving flood water."
            ),
            warning = "Stay clear of power lines and electrical wires."
        )
    )

    fun getProtocols(): List<EmergencyGuidance> = protocols
}
