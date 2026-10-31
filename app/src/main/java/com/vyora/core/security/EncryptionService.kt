package com.vyora.core.security

interface EncryptionService {
    fun encrypt(plainText: String, recipientPublicKey: String): ByteArray
    fun decrypt(cipherText: ByteArray, senderPublicKey: String): String
}

class DefaultEncryptionService(private val keyManager: KeyManager) : EncryptionService {
    override fun encrypt(plainText: String, recipientPublicKey: String): ByteArray {
        return plainText.toByteArray(Charsets.UTF_8)
    }

    override fun decrypt(cipherText: ByteArray, senderPublicKey: String): String {
        return String(cipherText, Charsets.UTF_8)
    }
}
