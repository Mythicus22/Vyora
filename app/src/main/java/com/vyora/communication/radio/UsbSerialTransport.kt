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
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.vyora.communication.MessageTransport
import com.vyora.communication.TransportState
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
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production-ready USB-to-Serial transport implementation for VYORA.
 *
 * Implements the VYORA Node Protocol (v1) defined in nrf.md and nrf_mesh_node.ino:
 * - 115200 baud, 8N1.
 * - JSON Lines (JSONL) framing: one UTF-8 JSON object per line ending in '\n'.
 * - Outgoing commands:
 *     status, set_profile, set_location, send, test (serial_loopback), set_node_id
 * - Incoming events:
 *     ready, status, node_seen, tx_status, message, delivered, delivery_timeout, error, test_result
 * - Monotonic 8-hex digit message sequence IDs per sender.
 * - Peer directory maintaining mapping between radio node IDs (8-hex) and VYORA user IDs.
 * - Backward compatibility fallback for bench-test sketches.
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
        const val MAX_RADIO_MESSAGE_BYTES = 280
    }

    data class PeerRecord(
        val nodeId: String,
        val userId: String = "",
        val callsign: String = "",
        val hops: Int = 1,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val rpd: Boolean? = null,
        val lastSeenMs: Long = System.currentTimeMillis()
    )

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

    // Local Node Telemetry from ESP32
    private val _localNodeId = MutableStateFlow<String?>(null)
    val localNodeId: StateFlow<String?> = _localNodeId.asStateFlow()

    private val _localMacAddress = MutableStateFlow<String?>(null)
    val localMacAddress: StateFlow<String?> = _localMacAddress.asStateFlow()

    private val _radioStatus = MutableStateFlow("unknown")
    val radioStatus: StateFlow<String> = _radioStatus.asStateFlow()

    private val _neighborsCount = MutableStateFlow(0)
    val neighborsCount: StateFlow<Int> = _neighborsCount.asStateFlow()

    // Exposes raw lines received from ESP32 for diagnostics or operator console
    private val _incomingLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingLines: SharedFlow<String> = _incomingLines.asSharedFlow()

    // Exposes parsed incoming messages for UI
    private val _receivedMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val receivedMessages: SharedFlow<Message> = _receivedMessages.asSharedFlow()

    // Exposes asynchronous delivery status updates: Pair<AppMessageUUID, MessageDeliveryStatus>
    private val _messageStatusUpdates = MutableSharedFlow<Pair<String, MessageDeliveryStatus>>(extraBufferCapacity = 64)
    val messageStatusUpdates: SharedFlow<Pair<String, MessageDeliveryStatus>> = _messageStatusUpdates.asSharedFlow()

    // Discovered peer table keyed by 8-hex radio node ID
    private val peerTable = ConcurrentHashMap<String, PeerRecord>()
    private val userIdToNodeId = ConcurrentHashMap<String, String>()
    private val callsignToNodeId = ConcurrentHashMap<String, String>()

    // Message tracking: hexMessageId -> appMessageId (UUID)
    private val messageIdMap = ConcurrentHashMap<String, String>()
    private val messageCounter = AtomicInteger(((System.currentTimeMillis() / 1000) and 0xFFFF).toInt())

    // Cached profile & location to auto-announce on handshake
    private var localUserId: String = ""
    private var localCallsign: String = ""
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var locationSharingEnabled: Boolean = true

    override val transportName: String = "ESP32 Mesh Node (115200 8N1 JSONL)"

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
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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

            try {
                port.dtr = true
                port.rts = true
            } catch (e: Exception) {
                Log.d(TAG, "DTR/RTS not supported on bridge: ${e.message}")
            }

            usbConnection = connection
            usbSerialPort = port

            synchronized(readBuffer) {
                readBuffer.setLength(0)
            }

            val manager = SerialInputOutputManager(port, this@UsbSerialTransport)
            ioManager = manager
            manager.start()

            _state.value = TransportState.CONNECTED
            val chipName = driver.javaClass.simpleName.replace("SerialDriver", "")
            _statusMessage.value = "ESP32 Linked ($chipName) @ 115200 8N1. Querying node status..."
            Log.i(TAG, "Serial port opened successfully: $chipName")

            // Initial node handshake per nrf.md
            performInitialHandshake()

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
        _localNodeId.value = null
        _localMacAddress.value = null
        _radioStatus.value = "offline"
        _neighborsCount.value = 0
    }

    /**
     * Sends a raw line ending with '\n' to the ESP32 node.
     */
    suspend fun sendLine(line: String): Boolean = withContext(Dispatchers.IO) {
        val port = usbSerialPort
        if (port == null || _state.value != TransportState.CONNECTED) {
            Log.w(TAG, "Cannot write: serial port not open")
            return@withContext false
        }

        return@withContext try {
            val payload = (line.trimEnd('\r', '\n') + "\n").toByteArray(StandardCharsets.UTF_8)
            port.write(payload, 2000)
            Log.d(TAG, "Sent over serial: $line")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Write failed over serial", e)
            false
        }
    }

    // --- Outgoing Protocol Commands (per nrf.md & nrf_mesh_node.ino) ---

    /**
     * Handshake immediately upon connecting:
     * 1. Query current ESP32 status {"cmd":"status"}
     * 2. Announce profile if configured {"cmd":"set_profile",...}
     * 3. Sync phone GPS location if available {"cmd":"set_location",...}
     */
    private fun performInitialHandshake() {
        scope.launch {
            // Request node status
            sendLine("{\"cmd\":\"status\"}")
            kotlinx.coroutines.delay(150)

            // If user profile is already configured, sync profile
            if (localUserId.isNotEmpty() && localCallsign.isNotEmpty()) {
                val profileCmd = JSONObject().apply {
                    put("cmd", "set_profile")
                    put("user_id", localUserId)
                    put("callsign", localCallsign)
                }.toString()
                sendLine(profileCmd)
                kotlinx.coroutines.delay(150)
            }

            // If location is fresh, sync location
            if (locationSharingEnabled && lastLatitude != null && lastLongitude != null) {
                val locCmd = JSONObject().apply {
                    put("cmd", "set_location")
                    put("enabled", true)
                    put("latitude", lastLatitude!!)
                    put("longitude", lastLongitude!!)
                }.toString()
                sendLine(locCmd)
            }
        }
    }

    /**
     * Informs ESP32 of local user profile without overwriting hardware node ID.
     */
    fun setUserProfile(userId: String, callsign: String) {
        localUserId = userId
        localCallsign = callsign
        if (_state.value == TransportState.CONNECTED) {
            scope.launch {
                val cmd = JSONObject().apply {
                    put("cmd", "set_profile")
                    put("user_id", userId)
                    put("callsign", callsign)
                }.toString()
                sendLine(cmd)
            }
        }
    }

    /**
     * Updates phone GPS coordinates sent to ESP32 for PROFILE announcements.
     * Coordinates are held in ESP32 RAM with a 120s TTL.
     */
    fun updateLocation(latitude: Double?, longitude: Double?, enabled: Boolean = true) {
        lastLatitude = latitude
        lastLongitude = longitude
        locationSharingEnabled = enabled

        if (_state.value == TransportState.CONNECTED) {
            scope.launch {
                val cmd = JSONObject().apply {
                    put("cmd", "set_location")
                    put("enabled", enabled && latitude != null && longitude != null)
                    if (enabled && latitude != null && longitude != null) {
                        put("latitude", latitude)
                        put("longitude", longitude)
                    }
                }.toString()
                sendLine(cmd)
            }
        }
    }

    /**
     * Sends {"cmd":"status"} to query active status from the node.
     */
    fun queryStatus() {
        scope.launch {
            sendLine("{\"cmd\":\"status\"}")
        }
    }

    /**
     * Performs serial loopback test {"cmd":"test","action":"serial_loopback","text":...}
     */
    fun sendLoopbackTest(text: String) {
        scope.launch {
            val cmd = JSONObject().apply {
                put("cmd", "test")
                put("action", "serial_loopback")
                put("text", text)
            }.toString()
            sendLine(cmd)
        }
    }

    /**
     * Explicit administrative override for node ID {"cmd":"set_node_id","node_id":...}
     */
    fun setNodeId(nodeId: String) {
        val clean = nodeId.trim().uppercase(Locale.US)
        if (clean.length == 8) {
            scope.launch {
                val cmd = JSONObject().apply {
                    put("cmd", "set_node_id")
                    put("node_id", clean)
                }.toString()
                sendLine(cmd)
            }
        }
    }

    /**
     * Generates a monotonic 8-hex digit message ID representing per-sender uint32 sequence.
     */
    private fun nextHexMessageId(): String {
        val nextVal = (messageCounter.incrementAndGet().toLong() and 0xFFFFFFFFL)
        return String.format(Locale.US, "%08X", nextVal)
    }

    /**
     * Resolves an arbitrary destination (VYO user ID, nickname, 8-hex node ID, or broadcast)
     * to a valid 8-hex node ID.
     */
    private fun resolveDestinationHex(destination: String): String {
        val clean = destination.trim().uppercase(Locale.US)
        if (clean.isEmpty() || clean == "00000000" || clean == "BROADCAST" || clean == "ALL") {
            return "00000000"
        }
        // Direct 8-hex radio node ID
        if (clean.matches(Regex("^[0-9A-F]{8}$"))) {
            return clean
        }
        val extracted = com.vyora.domain.usecase.CreateIdentityUseCase.extractNodeId(clean)
        if (extracted.matches(Regex("^[0-9A-F]{8}$")) && extracted != "00000000") {
            return extracted
        }
        // Check VYORA user ID mapping
        userIdToNodeId[destination]?.let { return it }
        userIdToNodeId[clean]?.let { return it }
        // Check callsign mapping
        callsignToNodeId[destination]?.let { return it }
        callsignToNodeId[clean]?.let { return it }

        // Fallback: flood broadcast so receiver node or relays can deliver
        return "00000000"
    }

    private fun clampUtf8ToBytes(text: String, maxBytes: Int): String {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        var length = maxBytes
        while (length > 0) {
            try {
                return String(bytes, 0, length, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                length--
            }
        }
        return text.take(maxBytes)
    }

    override suspend fun transmit(message: Message): MessageDeliveryStatus {
        if (_state.value != TransportState.CONNECTED) {
            return MessageDeliveryStatus.WAITING_FOR_ROUTE
        }

        val destinationHex = resolveDestinationHex(message.recipientId)
        val hexMsgId = nextHexMessageId()

        // Map hex sequence ID to the app's UUID message ID
        messageIdMap[hexMsgId] = message.id

        val bodyText = clampUtf8ToBytes(message.text, MAX_RADIO_MESSAGE_BYTES)

        val cmd = JSONObject().apply {
            put("cmd", "send")
            put("to", destinationHex)
            put("message_id", hexMsgId)
            put("text", bodyText)
        }.toString()

        val sent = sendLine(cmd)
        return if (sent) {
            MessageDeliveryStatus.QUEUED_LOCALLY
        } else {
            MessageDeliveryStatus.FAILED_EXPIRED
        }
    }

    override suspend fun broadcastSos(alert: SosAlert): Boolean {
        if (_state.value != TransportState.CONNECTED) return false

        val hexMsgId = nextHexMessageId()
        val lat = alert.latitude ?: 0.0
        val lng = alert.longitude ?: 0.0
        val payload = "SOS:${alert.category.name}:${alert.senderNickname}:$lat:$lng:${alert.note}"
        val bodyText = clampUtf8ToBytes(payload, MAX_RADIO_MESSAGE_BYTES)

        val cmd = JSONObject().apply {
            put("cmd", "send")
            put("to", "00000000") // 00000000 is broadcast
            put("message_id", hexMsgId)
            put("text", bodyText)
        }.toString()

        return sendLine(cmd)
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
     * Parses incoming JSONL lines from the ESP32 nrf_mesh_node firmware.
     */
    private fun processReceivedLine(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return
        Log.d(TAG, "ESP32 Line: $clean")

        scope.launch {
            _incomingLines.emit(clean)

            if (clean.startsWith("{") && clean.endsWith("}")) {
                try {
                    val json = JSONObject(clean)
                    handleJsonEvent(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse JSON line: $clean", e)
                }
            } else {
                // Backward-compatibility fallback for bench-test sketches
                handleBenchTestFallback(clean)
            }
        }
    }

    private suspend fun handleJsonEvent(json: JSONObject) {
        val eventType = json.optString("event")

        when (eventType) {
            "ready" -> {
                val id = json.optString("node_id")
                val mac = json.optString("mac")
                val radio = json.optString("radio")
                val rpd = json.optBoolean("rpd_supported", false)
                if (id.isNotEmpty()) _localNodeId.value = id
                if (mac.isNotEmpty()) _localMacAddress.value = mac
                _radioStatus.value = radio
                _statusMessage.value = "ESP32 Ready: Node $id${if (mac.isNotEmpty()) " (MAC: $mac)" else ""} • Radio: $radio (RPD: $rpd)"

                // Auto-sync profile and location upon ESP32 reboot/ready
                performInitialHandshake()
            }

            "status" -> {
                val id = json.optString("node_id")
                val mac = json.optString("mac")
                val radio = json.optString("radio")
                val neighbors = json.optInt("neighbors", 0)
                val userId = json.optString("user_id")
                val callsign = json.optString("callsign")
                val locShared = json.optBoolean("location_shared", false)

                if (id.isNotEmpty()) _localNodeId.value = id
                if (mac.isNotEmpty()) _localMacAddress.value = mac
                _radioStatus.value = radio
                _neighborsCount.value = neighbors

                _statusMessage.value = "ESP32 Online: Node $id${if (mac.isNotEmpty()) " (MAC: $mac)" else ""} • Peers: $neighbors • Radio: $radio"
            }

            "node_seen" -> {
                val nodeId = json.optString("node_id")
                val userId = json.optString("user_id")
                val callsign = json.optString("callsign")
                val hops = json.optInt("hops", 1)
                val now = System.currentTimeMillis()

                val rawLat = if (json.has("latitude") && !json.isNull("latitude")) json.optDouble("latitude")
                             else if (json.has("lat") && !json.isNull("lat")) json.optDouble("lat")
                             else null
                val rawLng = if (json.has("longitude") && !json.isNull("longitude")) json.optDouble("longitude")
                             else if (json.has("lon") && !json.isNull("lon")) json.optDouble("lon")
                             else if (json.has("lng") && !json.isNull("lng")) json.optDouble("lng")
                             else null
                val rpd = if (json.has("rpd") && !json.isNull("rpd")) json.optBoolean("rpd") else null

                val existing = peerTable[nodeId]
                val effectiveLat = if (rawLat != null && rawLat != 0.0) rawLat else existing?.latitude
                val effectiveLng = if (rawLng != null && rawLng != 0.0) rawLng else existing?.longitude
                val effectiveUserId = userId.ifEmpty { existing?.userId ?: "" }
                val effectiveCallsign = callsign.ifEmpty { existing?.callsign ?: "" }

                val record = PeerRecord(
                    nodeId = nodeId,
                    userId = effectiveUserId,
                    callsign = effectiveCallsign,
                    hops = hops,
                    latitude = effectiveLat,
                    longitude = effectiveLng,
                    rpd = rpd ?: existing?.rpd,
                    lastSeenMs = now
                )
                peerTable[nodeId] = record
                if (effectiveUserId.isNotEmpty()) userIdToNodeId[effectiveUserId] = nodeId
                if (effectiveCallsign.isNotEmpty()) callsignToNodeId[effectiveCallsign] = nodeId

                updateDiscoveredDevices()
                _statusMessage.value = "Peer Discovered: ${effectiveCallsign.ifEmpty { nodeId }} ($hops hop${if (hops > 1) "s" else ""})"
            }

            "tx_status" -> {
                val hexMsgId = json.optString("message_id")
                val status = json.optString("status")
                val appMsgId = messageIdMap[hexMsgId]

                if (appMsgId != null) {
                    when (status) {
                        "queued" -> _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.QUEUED_LOCALLY))
                        "transmitted" -> _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.SENT_TO_RELAY))
                        "transmit_error" -> _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.FAILED_EXPIRED))
                    }
                }
                _statusMessage.value = "Radio TX Status: $status ($hexMsgId)"
            }

            "delivered" -> {
                val hexMsgId = json.optString("message_id")
                val toNode = json.optString("to")
                val appMsgId = messageIdMap[hexMsgId]

                if (appMsgId != null) {
                    _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.DELIVERED))
                }
                _statusMessage.value = "Delivery Confirmed by $toNode ($hexMsgId)"
            }

            "delivery_timeout" -> {
                val hexMsgId = json.optString("message_id")
                val toNode = json.optString("to")
                val appMsgId = messageIdMap[hexMsgId]

                if (appMsgId != null) {
                    _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.FAILED_EXPIRED))
                }
                _statusMessage.value = "Delivery Timeout (no ACK from $toNode)"
            }

            "message" -> {
                val fromNode = json.optString("from")
                val toNode = json.optString("to")
                val hexMsgId = json.optString("message_id")
                val text = json.optString("text")

                val peer = peerTable[fromNode]
                val senderId = if (peer != null && peer.userId.isNotEmpty()) peer.userId else fromNode

                val isSos = text.startsWith("SOS:")
                val incomingMsg = Message(
                    id = "rx-$fromNode-$hexMsgId",
                    conversationId = senderId,
                    senderId = senderId,
                    recipientId = if (toNode == "00000000") "BROADCAST" else "ME",
                    text = text,
                    status = MessageDeliveryStatus.DELIVERED,
                    type = if (isSos) MessageType.EMERGENCY_ALERT else MessageType.NORMAL,
                    isFromMe = false
                )
                _receivedMessages.emit(incomingMsg)
                _statusMessage.value = "Radio RX from ${peer?.callsign ?: fromNode}: ${text.take(24)}..."
            }

            "test_result" -> {
                val mode = json.optString("mode")
                val status = json.optString("status")
                val text = json.optString("text")
                _statusMessage.value = "Test Result [$mode]: $status - $text"
            }

            "error" -> {
                val code = json.optString("code")
                val hexMsgId = json.optString("message_id")
                if (hexMsgId.isNotEmpty()) {
                    messageIdMap[hexMsgId]?.let { appMsgId ->
                        _messageStatusUpdates.emit(Pair(appMsgId, MessageDeliveryStatus.FAILED_EXPIRED))
                    }
                }
                _statusMessage.value = "ESP32 Error: $code"
            }
        }
    }

    private fun updateDiscoveredDevices() {
        val devices = peerTable.values.map { peer ->
            val reachability = if (peer.hops <= 1) ReachabilityStatus.REACHABLE_DIRECT else ReachabilityStatus.REACHABLE_RELAY
            // Approximate RSSI hint from coarse RPD indicator (threshold ~ -64 dBm)
            val rssiEstimate = when (peer.rpd) {
                true -> -60
                false -> -82
                null -> if (peer.hops <= 1) -70 else -85
            }

            NearbyDevice(
                deviceId = if (peer.userId.isNotEmpty()) peer.userId else peer.nodeId,
                nickname = if (peer.callsign.isNotEmpty()) peer.callsign else "Node-${peer.nodeId}",
                reachability = reachability,
                latitude = peer.latitude,
                longitude = peer.longitude,
                rssi = rssiEstimate,
                hopCount = peer.hops,
                lastSeenTimestamp = peer.lastSeenMs,
                isSimulated = false
            )
        }
        _discoveredDevices.value = devices
        _neighborsCount.value = devices.size
    }

    /**
     * Fallback for older bench test firmware output
     */
    private suspend fun handleBenchTestFallback(clean: String) {
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
                    type = MessageType.NORMAL,
                    isFromMe = false
                )
                _receivedMessages.emit(incomingMsg)
            }
            clean.startsWith("[sent] ") -> {
                val content = clean.removePrefix("[sent] ").trim()
                _statusMessage.value = "Radio TX ACK: $content"
            }
            clean.startsWith("[send failed]") -> {
                _statusMessage.value = "Radio TX Failed (No ACK from peer)"
            }
            else -> {
                _statusMessage.value = "Hardware: $clean"
            }
        }
    }
}
