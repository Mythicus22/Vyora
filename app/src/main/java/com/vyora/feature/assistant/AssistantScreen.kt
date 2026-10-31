package com.vyora.feature.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.vyora.ai.ModelDownloadState
import com.vyora.core.designsystem.theme.*
import com.vyora.feature.assistant.components.ModelStatusIndicator

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onTriggerSos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete SAFORA Model?", color = VyoraTextPrimary) },
            text = { Text("This will delete the 986 MB offline model file from app storage. You will need an internet connection to download it again.", color = VyoraTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteModel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VyoraEmergencyOrange)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = VyoraCyanSecondary)
                }
            },
            containerColor = VyoraNavyCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VyoraCyanSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, tint = VyoraCyanSecondary)
                    }
                    Column {
                        Text("SAFORA AI Assistant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                        Text("OFFLINE NEURAL LLM + RAG", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = VyoraCyanLight)
                    }
                }
                IconButton(
                    onClick = onTriggerSos,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VyoraEmergencyOrange)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            ModelStatusIndicator(statusText = uiState.statusMessage)
        }

        // Model Management Banner / Card
        ModelLifecycleCard(
            uiState = uiState,
            onDownloadClick = { viewModel.startModelDownload() },
            onCancelDownloadClick = { viewModel.cancelModelDownload() },
            onLoadModelClick = { viewModel.loadModel() },
            onUnloadModelClick = { viewModel.unloadModel() },
            onDeleteModelClick = { showDeleteConfirmDialog = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Emergency Disclaimer Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VyoraNavyHighlight.copy(alpha = 0.6f))
                .border(0.5.dp, VyoraNavyBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Disclaimer: Local AI guidance is supplementary. Always verify with official first-responders.",
                fontSize = 10.sp,
                color = VyoraTextSecondary
            )
        }

        // Triage Protocol Quick Chips
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

        // Chat History with Streaming Support
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
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
                                .fillMaxWidth(0.96f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VyoraNavyCard)
                                .border(1.dp, if (msg.isStreaming) VyoraCyanSecondary.copy(alpha = 0.8f) else VyoraNavyBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (msg.text.isEmpty() && msg.isStreaming) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = VyoraCyanSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Text("Generating offline response...", fontSize = 13.sp, color = VyoraCyanLight)
                                }
                            } else {
                                Text(
                                    text = msg.text,
                                    color = VyoraTextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }

                            if (msg.isStreaming) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = VyoraCyanSecondary,
                                    trackColor = VyoraNavyDark
                                )
                            }

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

        // Stop generation button banner when generating
        AnimatedVisibility(visible = uiState.isGenerating) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = { viewModel.cancelGeneration() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VyoraEmergencyOrange),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(VyoraEmergencyOrange))
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Generation")
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                placeholder = { Text("Ask SAFORA offline guidance...") },
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

@Composable
private fun ModelLifecycleCard(
    uiState: AssistantUiState,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onLoadModelClick: () -> Unit,
    onUnloadModelClick: () -> Unit,
    onDeleteModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VyoraNavyCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(VyoraNavyBorder))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (uiState.lifecycleStatus) {
                ModelLifecycleStatus.NOT_DOWNLOADED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SAFORA Neural Model (Qwen2 2B)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                            Text("Offline GGUF ~986 MB", fontSize = 11.sp, color = VyoraTextSecondary)
                        }
                        Button(
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = VyoraNavyDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", color = VyoraNavyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ModelLifecycleStatus.DOWNLOADING -> {
                    val progress = uiState.downloadProgress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Downloading SAFORA...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VyoraCyanSecondary)
                            if (progress is ModelDownloadState.Downloading) {
                                Text("${progress.percent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraCyanSecondary)
                            }
                        }
                        if (progress is ModelDownloadState.Downloading) {
                            LinearProgressIndicator(
                                progress = { progress.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = VyoraCyanSecondary,
                                trackColor = VyoraNavyHighlight
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val dlMb = progress.downloadedBytes / (1024 * 1024)
                                val totMb = progress.totalBytes / (1024 * 1024)
                                Text("$dlMb MB / $totMb MB (${progress.speedKBps} KB/s)", fontSize = 10.sp, color = VyoraTextSecondary)
                                Text(
                                    text = "Cancel",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VyoraEmergencyOrange,
                                    modifier = Modifier.clickable { onCancelDownloadClick() }
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = VyoraCyanSecondary
                            )
                        }
                    }
                }

                ModelLifecycleStatus.DOWNLOAD_FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            fontSize = 11.sp,
                            color = VyoraEmergencyOrange,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Retry", color = VyoraNavyDark, fontSize = 12.sp)
                        }
                    }
                }

                ModelLifecycleStatus.INSTALLED_UNLOADED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = VyoraMeshGreen, modifier = Modifier.size(14.dp))
                                Text("SAFORA Installed (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                            }
                            Text("986 MB in app storage", fontSize = 10.sp, color = VyoraTextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onDeleteModelClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VyoraTextSecondary)
                            ) {
                                Text("Delete", fontSize = 11.sp)
                            }
                            Button(
                                onClick = onLoadModelClick,
                                colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Load Model", color = VyoraNavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                ModelLifecycleStatus.LOADING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VyoraCyanSecondary, strokeWidth = 2.dp)
                        Text("Loading SAFORA into llama.cpp memory...", fontSize = 12.sp, color = VyoraCyanSecondary)
                    }
                }

                ModelLifecycleStatus.READY, ModelLifecycleStatus.GENERATING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(VyoraMeshGreen))
                            Text("SAFORA Active (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraMeshGreen)
                        }
                        TextButton(
                            onClick = onUnloadModelClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Unload", fontSize = 11.sp, color = VyoraTextSecondary)
                        }
                    }
                }

                ModelLifecycleStatus.ERROR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uiState.errorMessage ?: "Inference engine error", fontSize = 11.sp, color = VyoraEmergencyOrange, modifier = Modifier.weight(1f))
                        TextButton(onClick = onLoadModelClick) {
                            Text("Retry Load", fontSize = 11.sp, color = VyoraCyanSecondary)
                        }
                    }
                }
            }
        }
    }
}
