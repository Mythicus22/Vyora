package com.vyora.data.identity

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vyora.core.datastore.vyoraDataStore
import com.vyora.domain.model.UserIdentity
import com.vyora.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IdentityRepositoryImpl(private val context: Context) : IdentityRepository {

    private object Keys {
        val USER_ID = stringPreferencesKey("vyora_user_id")
        val NICKNAME = stringPreferencesKey("vyora_nickname")
        val PUBLIC_KEY = stringPreferencesKey("vyora_public_key")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("vyora_onboarding_completed")
    }

    override val identityFlow: Flow<UserIdentity?> = context.vyoraDataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID]
        val nick = prefs[Keys.NICKNAME]
        val key = prefs[Keys.PUBLIC_KEY] ?: ""
        if (id != null && nick != null) {
            UserIdentity(userId = id, nickname = nick, publicKey = key)
        } else {
            null
        }
    }

    override val isOnboardingCompleted: Flow<Boolean> = context.vyoraDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    override suspend fun getStoredUserId(): String? {
        var existing: String? = null
        context.vyoraDataStore.edit { prefs ->
            val id = prefs[Keys.USER_ID]
            if (!id.isNullOrEmpty()) {
                existing = id
            }
        }
        return existing
    }

    override suspend fun getOrCreatePermanentUserId(): String {
        return getStoredUserId() ?: ""
    }

    override suspend fun savePermanentNodeId(nodeId: String) {
        val clean = nodeId.trim().removePrefix("VYO-").removePrefix("vyo-").replace("-", "").replace(":", "").uppercase()
        val formatted = "VYO-$clean"
        context.vyoraDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = formatted
        }
    }

    override suspend fun saveIdentity(identity: UserIdentity) {
        context.vyoraDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = identity.userId
            prefs[Keys.NICKNAME] = identity.nickname
            prefs[Keys.PUBLIC_KEY] = identity.publicKey
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    override suspend fun clearIdentity() {
        context.vyoraDataStore.edit { prefs ->
            // Keep USER_ID permanent forever per user specification
            prefs.remove(Keys.NICKNAME)
            prefs.remove(Keys.PUBLIC_KEY)
            prefs[Keys.ONBOARDING_COMPLETED] = false
        }
    }
}
