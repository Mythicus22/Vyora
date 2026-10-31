package com.vyora.feature.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.vyora.domain.model.EmergencyCategory

@Composable
fun SosScreen(
    myUserId: String,
    myNickname: String,
    latitude: Double?,
    longitude: Double?,
    viewModel: SosViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(VyoraNavyDark)
                .border(1.dp, VyoraEmergencyOrange, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VyoraEmergencyOrange), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    }
                    Text(
                        text = if (uiState.activeAlert != null) "ACTIVE SOS BEACON" else "EMERGENCY SOS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraEmergencyOrange
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = VyoraTextSecondary)
                }
            }

            if (uiState.activeAlert != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VyoraNavyCard).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("BEACON BROADCASTING OVER SUB-GHZ RADIO", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VyoraEmergencyOrange)
                    Text("Alert ID: ${uiState.activeAlert?.alertId}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = VyoraTextPrimary)
                    Text("Category: ${uiState.activeAlert?.category?.label}", fontSize = 13.sp, color = VyoraTextPrimary)
                }
                Button(
                    onClick = {
                        viewModel.resolveSos()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VyoraMeshGreen, contentColor = VyoraNavyDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("TRANSMIT 'I AM SAFE' RESOLUTION", fontWeight = FontWeight.Bold)
                }
            } else if (uiState.countdownSeconds != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("BROADCASTING IN", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VyoraTextSecondary)
                    Text("${uiState.countdownSeconds}s", fontSize = 48.sp, fontWeight = FontWeight.Black, color = VyoraEmergencyOrange)
                    Button(
                        onClick = { viewModel.cancelCountdown() },
                        colors = ButtonDefaults.buttonColors(containerColor = VyoraNavyCard, contentColor = VyoraTextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCEL TRANSMISSION")
                    }
                }
            } else {
                Text("Select emergency classification:", fontSize = 13.sp, color = VyoraTextSecondary)
                EmergencyCategory.values().forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.selectedCategory == cat) VyoraNavyHighlight else VyoraNavyCard)
                            .border(1.dp, if (uiState.selectedCategory == cat) VyoraCyanSecondary else VyoraNavyBorder, RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectCategory(cat) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VyoraTextPrimary)
                        Text(cat.code, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VyoraCyanLight)
                    }
                }

                Button(
                    onClick = { viewModel.startCountdown(myUserId, myNickname, latitude, longitude) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VyoraEmergencyOrange, contentColor = Color.White)
                ) {
                    Text("INITIATE EMERGENCY SOS", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}
