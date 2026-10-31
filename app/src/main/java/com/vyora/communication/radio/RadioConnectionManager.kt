package com.vyora.communication.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RadioConnectionState {
    NO_RADIO,
    DETECTED,
    CONNECTED,
    TRANSMITTING,
    ERROR
}

class RadioConnectionManager {
    private val _connectionState = MutableStateFlow(RadioConnectionState.NO_RADIO)
    val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    fun updateState(state: RadioConnectionState) {
        _connectionState.value = state
    }
}
