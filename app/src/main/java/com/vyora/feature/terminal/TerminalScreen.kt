package com.vyora.feature.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.communication.MessageTransport
import com.vyora.communication.TransportState
import com.vyora.communication.radio.UsbSerialTransport
import com.vyora.core.designsystem.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(transport: MessageTransport? = null) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val usbTransport = transport as? UsbSerialTransport

    val stateFlow: StateFlow<TransportState> = usbTransport?.state ?: remember { MutableStateFlow(TransportState.DISCONNECTED) }
    val transportState by stateFlow.collectAsState()

    val statusFlow: StateFlow<String> = usbTransport?.statusMessage ?: remember { MutableStateFlow("Hardware Transport: Offline") }
    val statusMsg by statusFlow.collectAsState()

    val localNodeId by (usbTransport?.localNodeId ?: remember { MutableStateFlow(null) }).collectAsState()
    val localMacAddress by (usbTransport?.localMacAddress ?: remember { MutableStateFlow(null) }).collectAsState()
    val radioStatus by (usbTransport?.radioStatus ?: remember { MutableStateFlow("unknown") }).collectAsState()
    val neighborsCount by (usbTransport?.neighborsCount ?: remember { MutableStateFlow(0) }).collectAsState()

    var testInput by remember { mutableStateOf("{\"cmd\":\"status\"}") }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(usbTransport) {
        usbTransport?.incomingLines?.collect { line ->
            consoleLogs.add("[RX] $line")
            if (consoleLogs.size > 500) {
                consoleLogs.removeAt(0)
            }
            if (autoScroll && consoleLogs.isNotEmpty()) {
                listState.animateScrollToItem(consoleLogs.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ESP32 SERIAL TERMINAL",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VyoraTextPrimary
                )
                Text(
                    text = "Live RF Mesh & Hardware Serial Monitor (115200 8N1)",
                    fontSize = 11.sp,
                    color = VyoraCyanLight
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Copy all logs
                IconButton(
                    onClick = {
                        val all = consoleLogs.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(all))
                        Toast.makeText(context, "Terminal logs copied (${consoleLogs.size} lines)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VyoraNavyHighlight)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = VyoraCyanLight, modifier = Modifier.size(18.dp))
                }

                // Clear logs
                IconButton(
                    onClick = { consoleLogs.clear() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VyoraNavyHighlight)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear", tint = VyoraEmergencyOrange, modifier = Modifier.size(18.dp))
                }

                // Reconnect USB
                IconButton(
                    onClick = {
                        coroutineScope.launch { usbTransport?.connectAvailableDevice() }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VyoraNavyHighlight)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reconnect", tint = VyoraCyanSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Hardware Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (transportState == TransportState.CONNECTED) VyoraMeshGreen.copy(alpha = 0.15f) else VyoraEmergencyOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange)
                        )
                        Text(
                            text = if (transportState == TransportState.CONNECTED) "SERIAL ONLINE (115200 8N1)" else "SERIAL OFFLINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange
                        )
                    }
                    Text(
                        text = statusMsg,
                        fontSize = 11.sp,
                        color = VyoraTextSecondary,
                        maxLines = 1
                    )
                }
            }

            // Telemetry badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = VyoraNavyHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "NODE ID: ${localNodeId ?: "NONE"}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanSecondary
                    )
                }

                if (!localMacAddress.isNullOrEmpty()) {
                    Surface(
                        color = VyoraNavyHighlight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "MAC: $localMacAddress",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VyoraMeshGreen
                        )
                    }
                }

                Surface(
                    color = VyoraNavyHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "RADIO: ${radioStatus.uppercase()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (radioStatus == "ready") VyoraMeshGreen else VyoraEmergencyOrange
                    )
                }

                Surface(
                    color = VyoraNavyHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "PEERS: $neighborsCount",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = VyoraTextPrimary
                    )
                }
            }
        }

        // Quick Command Action Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    usbTransport?.queryStatus()
                    consoleLogs.add("[TX] {\"cmd\":\"status\"}")
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = VyoraNavyCard),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Query Status", fontSize = 11.sp, color = VyoraCyanLight)
            }

            FilledTonalButton(
                onClick = {
                    usbTransport?.sendLoopbackTest("terminal test")
                    consoleLogs.add("[TX] {\"cmd\":\"test\",\"action\":\"serial_loopback\",\"text\":\"terminal test\"}")
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = VyoraNavyCard),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Loopback Test", fontSize = 11.sp, color = VyoraMeshGreen)
            }

            FilledTonalButton(
                onClick = {
                    coroutineScope.launch {
                        val cmd = "{\"cmd\":\"send\",\"to\":\"00000000\",\"message_id\":\"FF01\",\"text\":\"PING_MESH\"}"
                        usbTransport?.sendLine(cmd)
                        consoleLogs.add("[TX] $cmd")
                    }
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = VyoraNavyCard),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Broadcast Ping", fontSize = 11.sp, color = VyoraCyanSecondary)
            }
        }

        // Live Console Log Window
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ESP SERIAL STREAM (${consoleLogs.size} MESSAGES)",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VyoraCyanLight
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-scroll", fontSize = 10.sp, color = VyoraTextSecondary)
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                    modifier = Modifier.height(20.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070B14))
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (consoleLogs.isEmpty()) {
                item {
                    Text(
                        text = "Awaiting messages from ESP32 serial bridge (115200 8N1)...\nPlug device via USB OTG adapter.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = VyoraTextTertiary
                    )
                }
            } else {
                itemsIndexed(consoleLogs) { _, entry ->
                    val color = when {
                        entry.startsWith("[TX]") -> VyoraCyanSecondary
                        entry.contains("\"event\":\"error\"") || entry.contains("[TX ERR]") -> VyoraEmergencyOrange
                        entry.contains("\"event\":\"delivered\"") || entry.contains("\"event\":\"node_seen\"") -> VyoraMeshGreen
                        entry.contains("\"event\":\"message\"") -> Color(0xFF64FFDA)
                        entry.startsWith("[RX]") -> VyoraCyanLight
                        else -> VyoraTextSecondary
                    }
                    Text(
                        text = entry,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = color
                    )
                }
            }
        }

        // Transmission Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                placeholder = { Text("Enter serial line / JSON...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VyoraCyanSecondary,
                    unfocusedBorderColor = VyoraNavyBorder,
                    focusedTextColor = VyoraTextPrimary,
                    unfocusedTextColor = VyoraTextPrimary
                )
            )
            Button(
                onClick = {
                    val line = testInput.trim()
                    if (line.isNotEmpty()) {
                        coroutineScope.launch {
                            val ok = usbTransport?.sendLine(line) ?: false
                            consoleLogs.add(if (ok) "[TX] $line" else "[TX ERR] $line")
                            if (autoScroll && consoleLogs.isNotEmpty()) {
                                listState.animateScrollToItem(consoleLogs.size - 1)
                            }
                        }
                    }
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
