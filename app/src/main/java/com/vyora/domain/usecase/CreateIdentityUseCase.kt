package com.vyora.domain.usecase

import com.vyora.domain.model.UserIdentity
import com.vyora.domain.repository.IdentityRepository
import java.security.SecureRandom
import java.util.Locale

class CreateIdentityUseCase(private val identityRepository: IdentityRepository) {

    fun generateUniqueUserId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(4)
        random.nextBytes(bytes)
        if (bytes.all { it == 0.toByte() }) bytes[3] = 1
        val hex = bytes.joinToString("") { "%02X".format(it) }
        return "VYO-$hex"
    }

    suspend operator fun invoke(nickname: String, userId: String = generateUniqueUserId()): UserIdentity {
        val identity = UserIdentity(userId = userId, nickname = nickname)
        identityRepository.saveIdentity(identity)
        return identity
    }

    companion object {
        fun extractNodeId(userId: String): String {
            val clean = userId.trim()
                .removePrefix("VYO-")
                .removePrefix("vyo-")
                .replace("-", "")
                .replace(":", "")
                .uppercase(Locale.US)
            return when {
                clean.length == 8 && clean.all { it in "0123456789ABCDEF" } -> clean
                clean.length == 12 && clean.all { it in "0123456789ABCDEF" } -> {
                    val low = clean.takeLast(8).toLongOrNull(16) ?: 0L
                    val high = clean.take(4).toLongOrNull(16) ?: 0L
                    val xor = ((low xor (high shl 16)) and 0xFFFFFFFFL)
                    "%08X".format(xor)
                }
                clean.length > 8 -> clean.takeLast(8)
                else -> clean.filter { it in "0123456789ABCDEF" }.padStart(8, '0').takeLast(8)
            }
        }
    }
}
