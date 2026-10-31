package com.vyora.communication.radio

class BluetoothTransport {
    fun scan(): List<String> = emptyList()
    fun connect(macAddress: String): Boolean = false
    fun disconnect() {}
}
