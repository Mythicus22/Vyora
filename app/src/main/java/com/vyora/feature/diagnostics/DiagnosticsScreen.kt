package com.vyora.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(transport: MessageTransport? = null) {
    val coroutineScope = rememberCoroutineScope()
    val usbTransport = transport as? UsbSerialTransport

    val transportState by (usbTransport?.state ?: remember { mutableStateOf(TransportState.DISCONNECTED) }).collectAsState()
    val statusMsg by (usbTransport?.statusMessage ?: remember { mutableStateOf("Hardware Transport: Offline") }).collectAsState()

    var testInput by remember { mutableStateOf("hello") }
    val consoleLogs = remember { mutableStateListOf<String>() }

    LaunchedEffect(usbTransport) {
        usbTransport?.incomingLines?.collect { line ->
            consoleLogs.add(0, "[RX] $line")
            if (consoleLogs.size > 100) consoleLogs.removeLast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("RF & Serial Diagnostics", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                Text("ESP32 DevKit ↔ nRF24L01 Serial Bridge", fontSize = 12.sp, color = VyoraCyanLight)
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

        // Hardware Status Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VyoraNavyHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (transportState == TransportState.CONNECTED) VyoraMeshGreen else VyoraEmergencyOrange,
                    modifier = Modifier.size(24.dp)
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
                        text = if (transportState == TransportState.CONNECTED) "LINK ONLINE" else "LINK OFFLINE",
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

        // Bench Test Transmission Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VyoraNavyCard)
                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                placeholder = { Text("Bench text (max 31 chars)...") },
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
                            consoleLogs.add(0, if (ok) "[TX] $line\\n" else "[TX ERR] $line")
                        }
                    }
                },
                enabled = transportState == TransportState.CONNECTED,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", fontWeight = FontWeight.Bold)
            }
        }

        // Live Console Log Window
        Text("LIVE RAW SERIAL TELEMETRY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VyoraCyanLight)
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
                        text = "Awaiting serial packets from ESP32 DevKit (115200 8N1)...",
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
                        entry.startsWith("[RX]") -> VyoraMeshGreen
                        else -> VyoraEmergencyOrange
                    }
                    Text(text = entry, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color)
                }
            }
        }
    }
}
