package com.vyora.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun DiagnosticsScreen(transport: MessageTransport? = null) {
    val coroutineScope = rememberCoroutineScope()
    val usbTransport = transport as? UsbSerialTransport

    val stateFlow: StateFlow<TransportState> = usbTransport?.state ?: remember { MutableStateFlow(TransportState.DISCONNECTED) }
    val transportState by stateFlow.collectAsState()

    val statusFlow: StateFlow<String> = usbTransport?.statusMessage ?: remember { MutableStateFlow("Hardware Transport: Offline") }
    val statusMsg by statusFlow.collectAsState()

    val localNodeId by (usbTransport?.localNodeId ?: remember { MutableStateFlow(null) }).collectAsState()
    val radioStatus by (usbTransport?.radioStatus ?: remember { MutableStateFlow("unknown") }).collectAsState()
    val neighborsCount by (usbTransport?.neighborsCount ?: remember { MutableStateFlow(0) }).collectAsState()

    var testInput by remember { mutableStateOf("{\"cmd\":\"status\"}") }
    val consoleLogs = remember { mutableStateListOf<String>() }

    LaunchedEffect(usbTransport) {
        usbTransport?.incomingLines?.collect { line ->
            consoleLogs.add(0, "[RX] $line")
            if (consoleLogs.size > 200) consoleLogs.removeAt(consoleLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ESP32 RF & Serial Console", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                Text("VYORA Node Protocol v1 (115200 8N1 JSONL)", fontSize = 11.sp, color = VyoraCyanLight)
            }
            IconButton(
                onClick = {
                    coroutineScope.launch { usbTransport?.connectAvailableDevice() }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VyoraNavyHighlight)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reconnect", tint = VyoraCyanSecondary)
            }
        }

        // Hardware Status & Node Telemetry Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VyoraNavyHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange,
                        modifier = Modifier.size(22.dp)
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
                            text = if (transportState == TransportState.CONNECTED) "USB SERIAL LINK ONLINE" else "USB SERIAL OFFLINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusMsg,
                        fontSize = 11.sp,
                        color = VyoraTextSecondary,
                        maxLines = 2
                    )
                }
            }

            // Node Telemetry Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = VyoraNavyHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "NODE ID: ${localNodeId ?: "PENDING"}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanSecondary
                    )
                }
                Surface(
                    color = VyoraNavyHighlight,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "RADIO: ${radioStatus.uppercase()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraTextPrimary
                    )
                }
            }
        }

        // Quick Command Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    usbTransport?.queryStatus()
                    consoleLogs.add(0, "[TX] {\"cmd\":\"status\"}")
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = VyoraNavyCard)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Query Status", fontSize = 11.sp, color = VyoraCyanLight)
            }

            FilledTonalButton(
                onClick = {
                    usbTransport?.sendLoopbackTest("serial parser check")
                    consoleLogs.add(0, "[TX] {\"cmd\":\"test\",\"action\":\"serial_loopback\",\"text\":\"serial parser check\"}")
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = VyoraNavyCard)
            ) {
                Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Loopback Test", fontSize = 11.sp, color = VyoraMeshGreen)
            }
        }

        // Transmission Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                placeholder = { Text("JSONL command or text...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
            Button(
                onClick = {
                    val line = testInput.trim()
                    if (line.isNotEmpty()) {
                        coroutineScope.launch {
                            val ok = usbTransport?.sendLine(line) ?: false
                            consoleLogs.add(0, if (ok) "[TX] $line" else "[TX ERR] $line")
                        }
                    }
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Live Console Log Window
        Text("LIVE JSONL SERIAL BUS LOGS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VyoraCyanLight)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VyoraNavyDark)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (consoleLogs.isEmpty()) {
                item {
                    Text(
                        text = "Awaiting JSONL packets from ESP32 DevKit (115200 8N1)...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = VyoraTextTertiary
                    )
                }
            } else {
                items(consoleLogs.size) { idx ->
                    val entry = consoleLogs[idx]
                    val color = when {
                        entry.startsWith("[TX]") -> VyoraCyanSecondary
                        entry.startsWith("[RX]") && entry.contains("\"event\":\"error\"") -> VyoraEmergencyOrange
                        entry.startsWith("[RX]") && entry.contains("\"event\":\"delivered\"") -> VyoraMeshGreen
                        entry.startsWith("[RX]") && entry.contains("\"event\":\"message\"") -> VyoraMeshGreen
                        entry.startsWith("[RX]") -> VyoraCyanLight
                        else -> VyoraEmergencyOrange
                    }
                    Text(text = entry, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = color)
                }
            }
        }
    }
}
