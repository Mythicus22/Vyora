package com.vyora.communication.radio

import android.hardware.usb.UsbDevice

class UsbSerialTransport {
    fun connect(device: UsbDevice): Boolean = false
    fun disconnect() {}
    fun write(bytes: ByteArray): Int = 0
}
