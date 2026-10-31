package com.vyora.communication.radio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.vyora.communication.MessageTransport
import com.vyora.communication.TransportState
import com.vyora.domain.model.EmergencyCategory
import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import com.vyora.domain.model.MessageType
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.model.ReachabilityStatus
import com.vyora.domain.model.SosAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Robust USB-to-Serial transport implementation for VYORA.
 *
 * Interfaces directly with the ESP32 DevKit running the bench test firmware (nrf_pa_tx / nrf_pa_rx).
 * Serial parameters per ANDROID_HARDWARE_INTEGRATION.md:
 * - Baud rate: 115200 baud
 * - Framing: 8 data bits, no parity, 1 stop bit (8N1)
 * - Line protocol: UTF-8 newline-terminated strings ('\n', max 31 ASCII chars for bench radio)
 * - Incoming stream: continuously buffered and split on '\n' (trimming trailing '\r')
 */
class UsbSerialTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) : MessageTransport, SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "UsbSerialTransport"
        private const val ACTION_USB_PERMISSION = "com.vyora.USB_PERMISSION"
        const val BAUD_RATE = 115200
        const val DATA_BITS = UsbSerialPort.DATABITS_8
        const val STOP_BITS = UsbSerialPort.STOPBITS_1
        const val PARITY = UsbSerialPort.PARITY_NONE
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var usbSerialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var ioManager: SerialInputOutputManager? = null

    private val readBuffer = StringBuilder()

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("Hardware: Scanning for connected ESP32...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    // Exposes raw lines received from ESP32 for diagnostics or messaging router
    private val _incomingLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingLines: SharedFlow<String> = _incomingLines.asSharedFlow()

    // Exposes parsed incoming messages for UI
    private val _receivedMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val receivedMessages: SharedFlow<Message> = _receivedMessages.asSharedFlow()

    override val transportName: String = "ESP32-USB-SERIAL (115200 8N1)"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            Log.i(TAG, "USB Permission granted for device: ${device.deviceName}")
                            scope.launch { openPort(device) }
                        } else {
                            Log.w(TAG, "USB Permission denied for device: ${device?.deviceName}")
                            _state.value = TransportState.ERROR
                            _statusMessage.value = "USB permission denied by operator"
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i(TAG, "USB Device Attached")
                    scope.launch { connectAvailableDevice() }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB Device Detached")
                    scope.launch { disconnect() }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
        }

        connectAvailableDevice()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        disconnect()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Enumerates connected USB drivers using UsbSerialProber
     */
    suspend fun connectAvailableDevice() = withContext(Dispatchers.IO) {
        _state.value = TransportState.CONNECTING
        _statusMessage.value = "Enumerating USB serial drivers..."

        val availableDrivers: List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            _state.value = TransportState.DISCONNECTED
            _statusMessage.value = "No ESP32 USB-serial bridge detected. Plug via OTG adapter."
            Log.d(TAG, "No USB drivers found")
            return@withContext
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            _statusMessage.value = "Requesting USB Host permission for ${device.productName ?: "ESP32"}..."
            requestPermission(device)
            return@withContext
        }

        openPortWithDriver(driver)
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private suspend fun openPort(device: UsbDevice) = withContext(Dispatchers.IO) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver != null) {
            openPortWithDriver(driver)
        } else {
            _state.value = TransportState.ERROR
            _statusMessage.value = "Unsupported USB serial bridge chip"
        }
    }

    private suspend fun openPortWithDriver(driver: UsbSerialDriver) = withContext(Dispatchers.IO) {
        try {
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                _state.value = TransportState.ERROR
                _statusMessage.value = "Cannot open connection to USB bridge"
                return@withContext
            }

            val port = driver.ports.firstOrNull()
            if (port == null) {
                connection.close()
                _state.value = TransportState.ERROR
                _statusMessage.value = "No serial ports available on device"
                return@withContext
            }

            port.open(connection)
            port.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)

            // Optional: assert DTR/RTS as required by some UART bridge chips
            try {
                port.dtr = true
                port.rts = true
            } catch (e: Exception) {
                Log.d(TAG, "DTR/RTS not supported on bridge: ${e.message}")
            }

            usbConnection = connection
            usbSerialPort = port

            // Start asynchronous I/O thread
            val manager = SerialInputOutputManager(port, this@UsbSerialTransport)
            ioManager = manager
            manager.start()

            _state.value = TransportState.CONNECTED
            val chipName = driver.javaClass.simpleName.replace("SerialDriver", "")
            _statusMessage.value = "ESP32 Linked ($chipName) @ 115200 8N1"
            Log.i(TAG, "Serial port opened successfully: $chipName")

            // Automatically populate peer node based on bench test protocol
            updateHardwareNodePresence("Radio-Peer", ReachabilityStatus.REACHABLE_DIRECT)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open serial port", e)
            _state.value = TransportState.ERROR
            _statusMessage.value = "Serial Open Error: ${e.message}"
            disconnect()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            ioManager?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ioManager", e)
        } finally {
            ioManager = null
        }

        try {
            usbSerialPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing port", e)
        } finally {
            usbSerialPort = null
        }

        try {
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            usbConnection = null
        }

        _state.value = TransportState.DISCONNECTED
        _statusMessage.value = "Hardware disconnected. Reconnect OTG cable."
        _discoveredDevices.value = emptyList()
    }

    /**
     * Sends newline-terminated text to the ESP32.
     * Per ANDROID_HARDWARE_INTEGRATION.md:
     * Writes UTF-8 text appended with '\n' (up to 31 ASCII chars for the current bench test radio).
     */
    suspend fun sendLine(line: String): Boolean = withContext(Dispatchers.IO) {
        val port = usbSerialPort
        if (port == null || _state.value != TransportState.CONNECTED) {
            Log.w(TAG, "Cannot write: serial port not open")
            return@withContext false
        }

        return@withContext try {
            val payload = (line.trimEnd('\r', '\n') + "\n").toByteArray(StandardCharsets.UTF_8)
            port.write(payload, 1500)
            Log.d(TAG, "Sent over serial: $line")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Write failed over serial", e)
            false
        }
    }

    override suspend fun transmit(message: Message): MessageDeliveryStatus {
        if (_state.value != TransportState.CONNECTED) {
            return MessageDeliveryStatus.WAITING_FOR_ROUTE
        }

        // Bench test protocol accepts up to 31 chars per radio transmission
        val payload = message.text.take(31)
        val success = sendLine(payload)
        return if (success) {
            MessageDeliveryStatus.DELIVERED
        } else {
            MessageDeliveryStatus.FAILED
        }
    }

    override suspend fun broadcastSos(alert: SosAlert): Boolean {
        val payload = "SOS:${alert.category.name}:${alert.senderNickname}".take(31)
        return sendLine(payload)
    }

    // --- SerialInputOutputManager.Listener Implementation ---

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return

        val chunk = String(data, StandardCharsets.UTF_8)
        synchronized(readBuffer) {
            readBuffer.append(chunk)

            var newlineIdx = readBuffer.indexOf('\n')
            while (newlineIdx >= 0) {
                val line = readBuffer.substring(0, newlineIdx).trimEnd('\r')
                readBuffer.delete(0, newlineIdx + 1)

                processReceivedLine(line)

                newlineIdx = readBuffer.indexOf('\n')
            }
        }
    }

    override fun onRunError(e: Exception?) {
        Log.e(TAG, "Serial IO Manager error: ${e?.message}")
        scope.launch {
            _state.value = TransportState.ERROR
            _statusMessage.value = "Serial error: ${e?.message ?: "IO failure"}"
            disconnect()
        }
    }

    /**
     * Parses output from the ESP32 firmware according to ANDROID_HARDWARE_INTEGRATION.md:
     * - `[received] reply` -> Incoming payload from remote radio node
     * - `[sent] hello` -> Confirmation that local radio transmitted packet
     * - `[send failed]` -> Local radio transmission failure
     * - `Radio ready; listening for NODEB messages.` -> Status line
     */
    private fun processReceivedLine(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return
        Log.d(TAG, "ESP32 Line: $clean")

        scope.launch {
            _incomingLines.emit(clean)

            when {
                clean.startsWith("[received] ") -> {
                    val content = clean.removePrefix("[received] ").trim()
                    _statusMessage.value = "Radio RX: $content"

                    val incomingMsg = Message(
                        id = "rx-${System.currentTimeMillis()}",
                        conversationId = "ESP32-PEER",
                        senderId = "ESP32-PEER",
                        recipientId = "ME",
                        text = content,
                        status = MessageDeliveryStatus.DELIVERED,
                        type = MessageType.TEXT,
                        isFromMe = false
                    )
                    _receivedMessages.emit(incomingMsg)
                    updateHardwareNodePresence("ESP32-PEER", ReachabilityStatus.REACHABLE_DIRECT)
                }
                clean.startsWith("[sent] ") -> {
                    val content = clean.removePrefix("[sent] ").trim()
                    _statusMessage.value = "Radio TX ACK: $content"
                }
                clean.startsWith("[send failed]") -> {
                    _statusMessage.value = "Radio TX Failed (No ACK from peer)"
                }
                clean.contains("Radio ready", ignoreCase = true) || clean.contains("serial bridge", ignoreCase = true) -> {
                    _statusMessage.value = "Hardware: $clean"
                }
                else -> {
                    _statusMessage.value = "Hardware: $clean"
                }
            }
        }
    }

    private fun updateHardwareNodePresence(peerId: String, status: ReachabilityStatus) {
        _discoveredDevices.update { current ->
            val existing = current.find { it.deviceId == peerId }
            if (existing != null) {
                current.map { if (it.deviceId == peerId) it.copy(reachability = status, rssi = -65) else it }
            } else {
                current + NearbyDevice(
                    deviceId = peerId,
                    nickname = "Remote nRF24 Node",
                    reachability = status,
                    latitude = 37.7750,
                    longitude = -122.4180,
                    rssi = -65,
                    hopCount = 1,
                    isSimulated = false
                )
            }
        }
    }
}
