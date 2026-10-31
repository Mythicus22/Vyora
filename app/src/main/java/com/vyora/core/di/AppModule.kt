package com.vyora.core.di

import android.content.Context
import com.vyora.communication.MessageTransport
import com.vyora.communication.simulation.SimulatedTransport
import com.vyora.core.common.AppDispatchers
import com.vyora.core.location.LocationRepository
import com.vyora.core.security.KeyManager
import com.vyora.data.identity.IdentityRepositoryImpl
import com.vyora.data.messaging.MessageRepositoryImpl
import com.vyora.domain.repository.IdentityRepository
import com.vyora.domain.repository.MessageRepository

object AppModule {
    fun provideDispatchers() = AppDispatchers()
    fun provideKeyManager() = KeyManager()
    fun provideTransport(): MessageTransport = SimulatedTransport()
    fun provideLocationRepository(context: Context) = LocationRepository(context)
    fun provideIdentityRepository(context: Context): IdentityRepository = IdentityRepositoryImpl(context)
    fun provideMessageRepository(transport: MessageTransport): MessageRepository = MessageRepositoryImpl(transport)
}
