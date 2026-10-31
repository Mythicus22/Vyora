package com.vyora.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore

class KeyManager {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vyora_identity_key"
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreateKeyPair(): KeyPair {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            kpg.generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    fun getPublicKeyHex(): String {
        return getOrCreateKeyPair().public.encoded.joinToString("") { "%02x".format(it) }
    }
}
