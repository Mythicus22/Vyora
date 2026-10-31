package com.vyora.domain.usecase

import com.vyora.domain.model.UserIdentity
import com.vyora.domain.repository.IdentityRepository
import java.security.SecureRandom

class CreateIdentityUseCase(private val identityRepository: IdentityRepository) {

    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generateUniqueUserId(): String {
        val random = SecureRandom()
        val p1 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val p2 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return "VYO-$p1-$p2"
    }

    suspend operator fun invoke(nickname: String, userId: String = generateUniqueUserId()): UserIdentity {
        val identity = UserIdentity(userId = userId, nickname = nickname)
        identityRepository.saveIdentity(identity)
        return identity
    }
}
