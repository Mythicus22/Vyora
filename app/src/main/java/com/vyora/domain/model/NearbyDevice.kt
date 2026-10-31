package com.vyora.domain.model

enum class ReachabilityStatus {
    REACHABLE_DIRECT,
    REACHABLE_RELAY,
    STALE,
    UNKNOWN
}

data class NearbyDevice(
    val deviceId: String,
    val nickname: String,
    val reachability: ReachabilityStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rssi: Int = -75,
    val hopCount: Int = 1,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false
)
