package com.vyora.ai
 
import com.vyora.domain.repository.EmergencyGuidance

/**
 * Chat template formatter for the fine-tuned SAFORA 2B language model (Qwen2 architecture).
 *
 * Implements the verified Qwen2 ChatML specification from Hugging Face model metadata:
 * - Special tokens: <|im_start|>, <|im_end|>, <|endoftext|>
 * - Turn structure:
 *     <|im_start|>system
 *     {system_instruction}<|im_end|>
 *     <|im_start|>user
 *     {user_query}<|im_end|>
 *     <|im_start|>assistant
 *     {response}<|im_end|>
 *
 * Manages conversation history within the context budget and integrates
 * local emergency knowledge protocols without leaking sensitive operational telemetry.
 */
object SaforaPromptFormatter {

    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"
    const val BOS_TOKEN = "<|endoftext|>"
    const val EOS_TOKEN = "<|im_end|>"

    const val DEFAULT_SYSTEM_PROMPT =
        "You are SAFORA, an offline tactical emergency AI assistant in VYORA. " +
        "Provide direct, concise, prioritized life-safety and first-aid instructions. " +
        "Focus on immediate action steps. Clearly state limitations when uncertain."

    fun buildSystemPrompt(emergencyProtocols: List<EmergencyGuidance> = emptyList()): String {
        val sb = StringBuilder()
        sb.append(DEFAULT_SYSTEM_PROMPT)
        if (emergencyProtocols.isNotEmpty()) {
            sb.append("\n\n[OFFLINE EMERGENCY PROTOCOLS]:\n")
            for (p in emergencyProtocols) {
                sb.append("TITLE: ").append(p.title).append(" (").append(p.category).append(")\n")
                sb.append("SUMMARY: ").append(p.summary).append("\n")
                if (p.steps.isNotEmpty()) {
                    sb.append("STEPS:\n")
                    p.steps.forEachIndexed { idx, step ->
                        sb.append("${idx + 1}. ").append(step).append("\n")
                    }
                }
                if (p.warning.isNotEmpty()) {
                    sb.append("WARNING: ").append(p.warning).append("\n")
                }
                sb.append("\n")
            }
        }
        return sb.toString().trim()
    }

    fun formatChatPrompt(
        history: List<Pair<String, String>>,
        currentMessage: String,
        emergencyProtocols: List<EmergencyGuidance> = emptyList(),
        maxCharLength: Int = 4096
    ): String {
        val sb = StringBuilder()
        val sysPrompt = buildSystemPrompt(emergencyProtocols)
        sb.append(IM_START).append("system\n").append(sysPrompt).append(IM_END).append("\n")

        for ((role, content) in history) {
            sb.append(IM_START).append(role.lowercase()).append("\n")
                .append(content.trim())
                .append(IM_END).append("\n")
        }

        sb.append(IM_START).append("user\n").append(currentMessage.trim()).append(IM_END).append("\n")
        sb.append(IM_START).append("assistant\n")

        val result = sb.toString()
        if (result.length > maxCharLength) {
            val systemPart = result.substring(0, result.indexOf(IM_END) + IM_END.length + 1)
            val remainder = result.substring(systemPart.length)
            val budget = maxCharLength - systemPart.length
            if (budget > 0) {
                val pruned = remainder.takeLast(budget)
                return systemPart + pruned
            }
        }
        return result
    }

    data class MessageTurn(
        val role: String, // "system", "user", "assistant"
        val content: String
    )

    /**
     * Formats a complete multi-turn conversation into the Qwen2 ChatML prompt format.
     */
    fun formatConversation(
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        history: List<MessageTurn>,
        currentUserQuery: String,
        relevantLocalProtocols: List<String> = emptyList(),
        maxEstimatedChars: Int = 7000
    ): String {
        val sb = StringBuilder()

        // 1. Build System Instruction with optional local emergency protocol context
        val fullSystemPrompt = if (relevantLocalProtocols.isNotEmpty()) {
            val contextBlock = relevantLocalProtocols.joinToString("\n\n")
            "$systemPrompt\n\n[RELEVANT LOCAL EMERGENCY PROTOCOLS]:\n$contextBlock"
        } else {
            systemPrompt
        }

        sb.append(IM_START).append("system\n")
        sb.append(fullSystemPrompt.trim())
        sb.append(IM_END).append("\n")

        // 2. Add recent conversation turns within context budget
        val recentTurns = history.takeLast(8)
        for (turn in recentTurns) {
            val role = turn.role.lowercase()
            if (role != "system") {
                sb.append(IM_START).append(role).append("\n")
                sb.append(turn.content.trim())
                sb.append(IM_END).append("\n")
            }
        }

        // 3. Append current user query
        sb.append(IM_START).append("user\n")
        sb.append(currentUserQuery.trim())
        sb.append(IM_END).append("\n")

        // 4. Append assistant turn starter
        sb.append(IM_START).append("assistant\n")

        val result = sb.toString()
        return if (result.length > maxEstimatedChars) {
            // Prune middle if oversized, preserving system header and latest turn
            val headerEnd = result.indexOf(IM_END) + IM_END.length + 1
            val tail = result.takeLast(maxEstimatedChars - headerEnd)
            result.substring(0, headerEnd) + tail
        } else {
            result
        }
    }

    /**
     * Formats a single-shot query with system instruction.
     */
    fun formatSinglePrompt(
        query: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        relevantContext: List<String> = emptyList()
    ): String {
        return formatConversation(
            systemPrompt = systemPrompt,
            history = emptyList(),
            currentUserQuery = query,
            relevantLocalProtocols = relevantContext
        )
    }

    /**
     * Cleans up generated text by stripping any leaked ChatML tokens or prefixes.
     */
    fun cleanGeneratedOutput(raw: String): String {
        return raw
            .replace(IM_START + "assistant", "")
            .replace(IM_START, "")
            .replace(IM_END, "")
            .replace(BOS_TOKEN, "")
            .trim()
    }
}
