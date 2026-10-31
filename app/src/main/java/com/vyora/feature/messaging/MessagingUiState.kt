package com.vyora.feature.messaging

import com.vyora.domain.model.Conversation
import com.vyora.domain.model.Message

data class MessagingUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentMessages: List<Message> = emptyList(),
    val activePeerId: String? = null,
    val inputText: String = ""
)
