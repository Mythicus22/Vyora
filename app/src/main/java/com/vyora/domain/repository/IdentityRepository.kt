package com.vyora.domain.repository

import com.vyora.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    val identityFlow: Flow<UserIdentity?>
    val isOnboardingCompleted: Flow<Boolean>
    suspend fun getStoredUserId(): String?
    suspend fun getOrCreatePermanentUserId(): String
    suspend fun savePermanentNodeId(nodeId: String)
    suspend fun saveIdentity(identity: UserIdentity)
    suspend fun clearIdentity()
}
