package com.vyora.ai

import com.vyora.domain.repository.EmergencyGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaforaPromptFormatterTest {

    @Test
    fun testQwen2ChatMLFormatting() {
        val prompt = SaforaPromptFormatter.formatChatPrompt(
            history = listOf(
                "user" to "What to do during an earthquake?",
                "assistant" to "Drop, Cover, and Hold On."
            ),
            currentMessage = "What if I am outside?",
            emergencyProtocols = emptyList()
        )

        // Verify ChatML special tokens
        assertTrue(prompt.contains("<|im_start|>system\n"))
        assertTrue(prompt.contains("<|im_end|>\n"))
        assertTrue(prompt.contains("<|im_start|>user\nWhat to do during an earthquake?<|im_end|>\n"))
        assertTrue(prompt.contains("<|im_start|>assistant\nDrop, Cover, and Hold On.<|im_end|>\n"))
        assertTrue(prompt.contains("<|im_start|>user\nWhat if I am outside?<|im_end|>\n"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testEmergencyProtocolInjectionInSystemPrompt() {
        val protocols = listOf(
            EmergencyGuidance(
                title = "Severe Bleeding & Hemorrhage",
                category = "First Aid",
                summary = "Direct pressure protocol to control life-threatening blood loss.",
                steps = listOf(
                    "Apply continuous firm direct pressure over wound with clean cloth.",
                    "If limb bleeding is uncontrolled, apply tourniquet 2-3 inches above wound."
                ),
                warning = "DO NOT loosen a tourniquet once applied."
            )
        )

        val systemPrompt = SaforaPromptFormatter.buildSystemPrompt(protocols)
        assertTrue(systemPrompt.contains("OFFLINE EMERGENCY PROTOCOLS"))
        assertTrue(systemPrompt.contains("Severe Bleeding & Hemorrhage"))
        assertTrue(systemPrompt.contains("Apply continuous firm direct pressure"))
        assertTrue(systemPrompt.contains("DO NOT loosen a tourniquet once applied."))
    }

    @Test
    fun testContextWindowPruning() {
        val longHistory = (1..50).map { i ->
            "user" to "Long question number $i " + "x".repeat(100)
        }

        val prompt = SaforaPromptFormatter.formatChatPrompt(
            history = longHistory,
            currentMessage = "Final query",
            emergencyProtocols = emptyList(),
            maxCharLength = 1000
        )

        assertTrue(prompt.length <= 2000)
        assertTrue(prompt.contains("Final query"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }
}
