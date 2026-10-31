package com.vyora.feature.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*
import com.vyora.domain.model.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagingViewModel,
    onOpenChat: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val cleanQuery = searchQuery.trim().uppercase()
    val filteredConversations = remember(uiState.conversations, cleanQuery) {
        if (cleanQuery.isEmpty()) uiState.conversations
        else uiState.conversations.filter {
            it.recipientId.contains(cleanQuery, ignoreCase = true) ||
            it.recipientNickname.contains(cleanQuery, ignoreCase = true) ||
            it.lastMessageText.contains(cleanQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = VyoraNavyDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = VyoraCyanSecondary,
                contentColor = VyoraNavyDark
            ) {
                Icon(imageVector = Icons.Default.AddComment, contentDescription = "New Conversation")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Mesh Communications", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                Text("Store-and-forward peer addressed channels over RF", fontSize = 12.sp, color = VyoraCyanLight)
            }

            // Search Bar for unique ID, MAC address, or callsign
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search node ID, MAC, or callsign...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VyoraCyanLight) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = VyoraCyanLight)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VyoraCyanSecondary,
                    unfocusedBorderColor = VyoraNavyBorder,
                    focusedTextColor = VyoraTextPrimary,
                    unfocusedTextColor = VyoraTextPrimary
                )
            )

            // Direct Search Match Action Card (if user typed an ID that is not yet an ongoing conversation)
            if (cleanQuery.length >= 4 && filteredConversations.none { it.recipientId.equals(cleanQuery, ignoreCase = true) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            val targetId = if (cleanQuery.matches(Regex("^[0-9A-F]{8}$"))) "VYO-$cleanQuery" else cleanQuery
                            onOpenChat(targetId)
                        },
                    colors = CardDefaults.cardColors(containerColor = VyoraNavyHighlight),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(VyoraCyanSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AddComment, contentDescription = null, tint = VyoraNavyDark, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Mesh Chat with ID:", fontSize = 11.sp, color = VyoraTextSecondary)
                            Text(cleanQuery, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VyoraCyanLight)
                        }
                        Button(
                            onClick = {
                                val targetId = if (cleanQuery.matches(Regex("^[0-9A-F]{8}$"))) "VYO-$cleanQuery" else cleanQuery
                                onOpenChat(targetId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. ACTIVE DISCOVERED MESH NODES (RF Heartbeats)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.discoveredDevices.isNotEmpty()) VyoraMeshGreen else VyoraEmergencyOrange)
                        )
                        Text(
                            text = if (uiState.discoveredDevices.isNotEmpty()) "ACTIVE NEARBY NODES (${uiState.discoveredDevices.size})" else "RADIO MESH SCANNER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.discoveredDevices.isNotEmpty()) VyoraMeshGreen else VyoraCyanLight,
                            letterSpacing = 1.sp
                        )
                    }
                    if (uiState.discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Tap node to chat",
                            fontSize = 11.sp,
                            color = VyoraCyanLight
                        )
                    }
                }

                if (uiState.discoveredDevices.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VyoraNavyCard)
                            .border(0.5.dp, VyoraNavyBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = VyoraEmergencyOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Listening on 2.4GHz RF (Ch 76)... Beacons appear here when nearby nodes transmit.",
                            fontSize = 11.sp,
                            color = VyoraTextSecondary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.discoveredDevices.forEach { dev ->
                            Card(
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { onOpenChat(dev.deviceId) },
                                colors = CardDefaults.cardColors(containerColor = VyoraNavyCard),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VyoraMeshGreen.copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dev.nickname,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = VyoraTextPrimary,
                                            maxLines = 1
                                        )
                                        Surface(
                                            color = VyoraMeshGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (dev.hopCount <= 1) "DIRECT" else "${dev.hopCount} HOPS",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VyoraMeshGreen
                                            )
                                        }
                                    }

                                    Text(
                                        text = "ID: ${dev.deviceId}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = VyoraCyanLight
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${dev.rssi} dBm",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = VyoraTextSecondary
                                        )
                                        val hasGps = dev.latitude != null && dev.longitude != null && (dev.latitude != 0.0 || dev.longitude != 0.0)
                                        Text(
                                            text = if (hasGps) "GPS FIX" else "RF PROXIMITY",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = if (hasGps) VyoraMeshGreen else VyoraCyanSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. CONVERSATIONS HEADER & LIST
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MESSAGING CHANNELS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VyoraTextSecondary,
                    letterSpacing = 1.sp
                )
                if (filteredConversations.isNotEmpty()) {
                    Text(
                        text = "${filteredConversations.size} active",
                        fontSize = 11.sp,
                        color = VyoraCyanLight
                    )
                }
            }

            if (filteredConversations.isEmpty() && searchQuery.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = VyoraNavyCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VyoraNavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = VyoraCyanLight,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No Previous Chat History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = VyoraTextPrimary
                        )
                        Text(
                            text = "Tap any active node above to start communicating, or tap '+' below to enter an address.",
                            fontSize = 12.sp,
                            color = VyoraTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredConversations) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VyoraNavyCard)
                                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                                .clickable { onOpenChat(conv.recipientId) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(VyoraNavyHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(conv.recipientNickname.take(2).uppercase(), fontWeight = FontWeight.Bold, color = VyoraCyanSecondary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conv.recipientNickname, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VyoraTextPrimary)
                                Text("ID: ${conv.recipientId}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = VyoraCyanLight)
                                Text(conv.lastMessageText, fontSize = 13.sp, color = VyoraTextSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            var inputId by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val aliveNodes = uiState.discoveredDevices

            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = VyoraNavyCard,
                title = { Text("Start Mesh Conversation", color = VyoraTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Select an active mesh node or enter an alive node ID:",
                            fontSize = 12.sp,
                            color = VyoraTextSecondary
                        )

                        if (aliveNodes.isNotEmpty()) {
                            Text(
                                text = "ACTIVE DISCOVERED NODES:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VyoraCyanSecondary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                aliveNodes.forEach { dev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(VyoraNavyHighlight)
                                            .clickable {
                                                inputId = dev.deviceId
                                                errorMessage = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(dev.nickname, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VyoraTextPrimary)
                                            Text(dev.deviceId, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = VyoraCyanLight)
                                        }
                                        Text(
                                            text = "${dev.hopCount} hop",
                                            fontSize = 11.sp,
                                            color = VyoraMeshGreen
                                        )
                                    }
                                }
                            }
                            Divider(color = VyoraNavyBorder, thickness = 0.5.dp)
                        }

                        OutlinedTextField(
                            value = inputId,
                            onValueChange = {
                                inputId = it.uppercase()
                                errorMessage = null
                            },
                            label = { Text("Node ID") },
                            placeholder = { Text("e.g. 12AB34CD or VYO-...") },
                            singleLine = true,
                            isError = errorMessage != null,
                            supportingText = errorMessage?.let { { Text(it, color = VyoraEmergencyOrange) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VyoraCyanSecondary,
                                unfocusedBorderColor = VyoraNavyBorder,
                                focusedTextColor = VyoraTextPrimary,
                                unfocusedTextColor = VyoraTextPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = inputId.trim()
                            if (clean.isEmpty()) {
                                errorMessage = "Please select or enter a node ID or MAC."
                            } else if (!viewModel.isDeviceValid(clean)) {
                                errorMessage = "Invalid format. Enter an 8-hex node ID, MAC address, or VYO-XXXX."
                            } else {
                                showDialog = false
                                val targetId = if (clean.matches(Regex("^[0-9A-F]{8}$"))) "VYO-$clean" else clean
                                onOpenChat(targetId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
                    ) {
                        Text("Create Channel")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel", color = VyoraTextSecondary) }
                }
            )
        }
    }
}
