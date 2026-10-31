package com.vyora.ai

interface AiService {
    val isReady: Boolean
    suspend fun getResponse(prompt: String): String
}
