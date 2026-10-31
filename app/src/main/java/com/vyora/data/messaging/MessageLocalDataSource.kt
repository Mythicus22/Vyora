package com.vyora.data.messaging

import com.vyora.core.database.dao.MessageDao
import com.vyora.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

class MessageLocalDataSource(private val messageDao: MessageDao) {
    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun saveMessage(entity: MessageEntity) {
        messageDao.insertMessage(entity)
    }

    suspend fun updateStatus(id: String, status: String) {
        messageDao.updateMessageStatus(id, status)
    }
}
