package com.vyora.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
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
import com.vyora.core.designsystem.theme.*
import com.vyora.feature.assistant.components.ModelStatusIndicator

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onTriggerSos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(VyoraCyanSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, tint = VyoraCyanSecondary)
                    }
                    Column {
                        Text("AI Emergency Assistant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                        Text("OFFLINE DETERMINISTIC RAG", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = VyoraCyanLight)
                    }
                }
                IconButton(
                    onClick = onTriggerSos,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VyoraEmergencyOrange)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ModelStatusIndicator(statusText = uiState.statusMessage)
        }

        // Triage Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.quickProtocols) { p ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(VyoraNavyCard)
                        .border(1.dp, VyoraCyanSecondary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .clickable { viewModel.sendQuery(p.category) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = p.category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VyoraCyanSecondary)
                }
            }
        }

        // Chat History
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(uiState.chatHistory) { msg ->
                if (msg.isUser) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(VyoraCyanSecondary).padding(12.dp)
                        ) {
                            Text(text = msg.text, color = VyoraNavyDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VyoraNavyCard)
                                .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = msg.text, color = VyoraTextPrimary, fontSize = 14.sp)
                            msg.guidance?.let { g ->
                                if (g.steps.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(VyoraNavyHighlight).padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("ACTION STEPS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyoraMeshGreen)
                                        g.steps.forEachIndexed { i, s ->
                                            Text("${i + 1}. $s", fontSize = 12.sp, color = VyoraTextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                placeholder = { Text("Query local emergency guidance...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = { viewModel.sendQuery() },
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(VyoraCyanSecondary)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = VyoraNavyDark)
            }
        }
    }
}
