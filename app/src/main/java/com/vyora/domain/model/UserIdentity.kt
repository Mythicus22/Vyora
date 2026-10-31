package com.vyora.domain.model

data class UserIdentity(
    val userId: String,
    val nickname: String,
    val publicKey: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
