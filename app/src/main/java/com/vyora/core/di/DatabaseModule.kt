package com.vyora.core.di

import android.content.Context
import androidx.room.Room
import com.vyora.core.common.Constants
import com.vyora.core.database.VyoraDatabase
import com.vyora.core.database.dao.MessageDao

object DatabaseModule {
    @Volatile
    private var INSTANCE: VyoraDatabase? = null

    fun provideDatabase(context: Context): VyoraDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                VyoraDatabase::class.java,
                Constants.DATABASE_NAME
            ).fallbackToDestructiveMigration().build()
            INSTANCE = instance
            instance
        }
    }

    fun provideMessageDao(database: VyoraDatabase): MessageDao {
        return database.messageDao()
    }
}
