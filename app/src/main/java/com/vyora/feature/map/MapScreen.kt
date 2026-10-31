package com.vyora.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*
import com.vyora.domain.model.NearbyDevice
import com.vyora.domain.model.ReachabilityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStartChat: (String) -> Unit,
    onTriggerSos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDirectIdDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
    ) {
        // Radar Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.45f)
            val maxRadius = size.width * 0.48f

            listOf(0.33f, 0.66f, 1.0f).forEach { ratio ->
                drawCircle(
                    color = VyoraCyanSecondary.copy(alpha = 0.2f),
                    radius = maxRadius * ratio,
                    center = center,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }
            drawLine(VyoraCyanSecondary.copy(alpha = 0.25f), Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1.dp.toPx())
            drawLine(VyoraCyanSecondary.copy(alpha = 0.25f), Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1.dp.toPx())
            drawCircle(VyoraCyanSecondary.copy(alpha = 0.35f), 18.dp.toPx(), center)
            drawCircle(VyoraCyanSecondary, 7.dp.toPx(), center)
        }

        // Tactical Markers
        uiState.nearbyDevices.forEachIndexed { index, device ->
            val angleRad = (index * 85.0 + 30.0) * (Math.PI / 180.0)
            val distFraction = 0.25 + (index % 3) * 0.28
            val markerColor = when (device.reachability) {
                ReachabilityStatus.REACHABLE_DIRECT -> VyoraMeshGreen
                ReachabilityStatus.REACHABLE_RELAY -> VyoraCyanSecondary
                else -> Color(0xFFFACC15)
            }

            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (Math.cos(angleRad) * 130 * distFraction).dp,
                            y = (Math.sin(angleRad) * 130 * distFraction - 40).dp
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(VyoraNavyCard.copy(alpha = 0.9f))
                        .border(1.dp, markerColor, RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectDevice(device) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(markerColor))
                        Text(device.nickname, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                    }
                }
            }
        }

        // HUD Bar
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VyoraNavyCard.copy(alpha = 0.92f))
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(VyoraMeshGreen))
                    Text("ACTIVE MESH: ${uiState.nearbyDevices.size} NODES", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraMeshGreen)
                }
                IconButton(onClick = { showDirectIdDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Address by ID", tint = VyoraCyanSecondary)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VyoraNavyContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiState.myCoordinates?.let { String.format("GPS: %.4f°N, %.4f°W", it.latitude, it.longitude) } ?: "ACQUIRING FIX...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = VyoraTextPrimary
                )
                Text(text = "RADAR: 4.8 km", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VyoraCyanLight)
            }
        }

        // Floating Action Buttons
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = { viewModel.recenterGps() },
                containerColor = VyoraNavyCard,
                contentColor = VyoraCyanSecondary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Recenter")
            }

            FloatingActionButton(
                onClick = onTriggerSos,
                containerColor = VyoraEmergencyOrange,
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                    Text("SOS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Bottom Sheet for selected node
        uiState.selectedDevice?.let { dev ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectDevice(null) },
                containerColor = VyoraNavyCard
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(dev.nickname, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                    Text("ID: ${dev.deviceId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = VyoraCyanLight)
                    Button(
                        onClick = {
                            val id = dev.deviceId
                            viewModel.selectDevice(null)
                            onStartChat(id)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OPEN SECURE MESH CHAT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showDirectIdDialog) {
            var directInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showDirectIdDialog = false },
                containerColor = VyoraNavyCard,
                title = { Text("Direct Node Addressing", color = VyoraTextPrimary) },
                text = {
                    OutlinedTextField(
                        value = directInput,
                        onValueChange = { directInput = it.uppercase() },
                        placeholder = { Text("VYO-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val clean = directInput.trim()
                        if (clean.isNotEmpty()) {
                            showDirectIdDialog = false
                            onStartChat(clean)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)) {
                        Text("Open Chat")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDirectIdDialog = false }) { Text("Cancel", color = VyoraTextSecondary) }
                }
            )
        }
    }
}
