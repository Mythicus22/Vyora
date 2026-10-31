package com.vyora.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vyora.core.database.dao.MessageDao
import com.vyora.core.database.entity.MessageEntity

@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VyoraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
