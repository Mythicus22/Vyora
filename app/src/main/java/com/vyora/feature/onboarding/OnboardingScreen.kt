package com.vyora.feature.onboarding

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiOff
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
import com.vyora.core.designsystem.theme.*
import com.vyora.feature.onboarding.components.IdentityCard

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var loraRadioArmed by remember { mutableStateOf(true) }
    var gnssArmed by remember { mutableStateOf(true) }
    var localVectorDbArmed by remember { mutableStateOf(true) }
    var usbSerialArmed by remember { mutableStateOf(true) }

    val roles = listOf(
        "Field Operator / Responder",
        "Medical / Triage Unit",
        "Fixed Relay / Outpost",
        "Command / Tactical Lead"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top System Telemetry Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(VyoraNavyContainer)
                        .border(0.5.dp, VyoraNavyBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(VyoraMeshGreen)
                    )
                    Text(
                        text = "ESP32 USB SERIAL READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanSecondary,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "AIRGAP SECURED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = VyoraTextSecondary
                    )
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Air-gapped",
                        tint = VyoraMeshGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Hero Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VyoraNavyContainer)
                        .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "VYORA Shield",
                        tint = VyoraEmergencyOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(VyoraNavyHighlight)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "NODE PROVISIONING & SETUP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanLight,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tactical Mesh Node Setup",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VyoraTextPrimary
                )
                Text(
                    text = "Configure your node's callsign, tactical role, and hardware radio parameters for peer-to-peer resilience.",
                    fontSize = 13.sp,
                    color = VyoraTextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                )
            }

            // Section 1: Cryptographic Identity Card
            IdentityCard(
                userId = uiState.generatedUserId,
                onRegenerate = { viewModel.regenerateUserId() },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(uiState.generatedUserId))
                    Toast.makeText(context, "Node ID Copied", Toast.LENGTH_SHORT).show()
                }
            )

            // Section 2: Tactical Nickname / Callsign Input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "OPERATOR CALLSIGN / HANDLE *",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = VyoraCyanLight,
                    letterSpacing = 1.sp
                )
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = { viewModel.onNicknameChanged(it) },
                    placeholder = { Text("e.g. Echo-Actual (Jordan M.)", color = VyoraTextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VyoraNavyDark,
                        unfocusedContainerColor = VyoraNavyDark,
                        focusedBorderColor = VyoraCyanSecondary,
                        unfocusedBorderColor = VyoraNavyBorder,
                        focusedTextColor = VyoraTextPrimary,
                        unfocusedTextColor = VyoraTextPrimary,
                        cursorColor = VyoraCyanSecondary
                    )
                )
                Text(
                    text = "Peers in the RF radio mesh will see this handle attached to your verified packets.",
                    fontSize = 11.sp,
                    color = VyoraTextSecondary
                )
            }

            // Section 3: Tactical Operational Role
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = VyoraCyanSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TACTICAL ROLE & DISPOSITION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = VyoraCyanSecondary,
                        letterSpacing = 1.sp
                    )
                }

                roles.forEach { role ->
                    val isSelected = uiState.emergencyRole == role
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) VyoraNavyHighlight else VyoraNavyDark)
                            .border(
                                1.dp,
                                if (isSelected) VyoraCyanSecondary else VyoraNavyBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.onEmergencyRoleChanged(role) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = role,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) VyoraTextPrimary else VyoraTextSecondary
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = VyoraCyanSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Section 4: Radio & Hardware Connection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = VyoraCyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "RADIO INTERFACES & HARDWARE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = VyoraCyanSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "115200 8N1",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = VyoraMeshGreen
                    )
                }

                // Toggle 1: USB Serial OTG to ESP32
                HardwareToggleRow(
                    icon = Icons.Default.Usb,
                    title = "USB Serial ESP32 Bridge",
                    subtitle = "CP210x / CH340 OTG Host Communication",
                    checked = usbSerialArmed,
                    onCheckedChange = { usbSerialArmed = it }
                )

                // Toggle 2: nRF24L01+ Radio Driver
                HardwareToggleRow(
                    icon = Icons.Default.Radio,
                    title = "nRF24L01+ / Sub-GHz Transceiver",
                    subtitle = "Direct Link (NODEA ↔ NODEB) Active",
                    checked = loraRadioArmed,
                    onCheckedChange = { loraRadioArmed = it }
                )

                // Toggle 3: GNSS Positioning
                HardwareToggleRow(
                    icon = Icons.Default.SatelliteAlt,
                    title = "Offline GNSS Positioning",
                    subtitle = "Hardware GPS Fix for Tactical Radar Map",
                    checked = gnssArmed,
                    onCheckedChange = { gnssArmed = it }
                )

                // Toggle 4: Local Vector Knowledge
                HardwareToggleRow(
                    icon = Icons.Default.Storage,
                    title = "On-Device Protocol Engine",
                    subtitle = "Offline Emergency Guides & Store-and-Forward",
                    checked = localVectorDbArmed,
                    onCheckedChange = { localVectorDbArmed = it }
                )
            }

            // Section 5: Emergency Broadcast Note / Medical Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VyoraNavyCard)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "EMERGENCY BROADCAST NOTE / MEDICAL INFO",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = VyoraCyanLight,
                    letterSpacing = 1.sp
                )
                OutlinedTextField(
                    value = uiState.emergencyNote,
                    onValueChange = { viewModel.onEmergencyNoteChanged(it) },
                    placeholder = { Text("e.g. Blood type O+, carry tourniquet & AED", color = VyoraTextTertiary) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VyoraNavyDark,
                        unfocusedContainerColor = VyoraNavyDark,
                        focusedBorderColor = VyoraCyanSecondary,
                        unfocusedBorderColor = VyoraNavyBorder,
                        focusedTextColor = VyoraTextPrimary,
                        unfocusedTextColor = VyoraTextPrimary,
                        cursorColor = VyoraCyanSecondary
                    )
                )
                Text(
                    text = "Included automatically in packet payloads when you trigger an emergency SOS alert.",
                    fontSize = 11.sp,
                    color = VyoraTextSecondary
                )
            }

            // Section 6: Local Neural Engine Status Callout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VyoraNavyContainer)
                    .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VyoraNavyHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = VyoraCyanSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "AIR-GAPPED AI ENGINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VyoraTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(VyoraNavyBorder)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "READY",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = VyoraMeshGreen
                            )
                        }
                    }
                    Text(
                        text = "Vetted emergency triage guides run 100% on-device without internet access.",
                        fontSize = 11.sp,
                        color = VyoraTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Persistent CTA Footer Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = VyoraNavyCard,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VyoraNavyBorder)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = { viewModel.completeOnboarding(onNavigateToHome) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VyoraCyanSecondary,
                        contentColor = VyoraNavyDark
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "INITIALIZE & ACTIVATE TERMINAL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VyoraNavyDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) VyoraMeshGreen else VyoraTextTertiary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VyoraTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (checked) VyoraMeshGreen else VyoraTextTertiary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VyoraNavyDark,
                checkedTrackColor = VyoraMeshGreen,
                uncheckedThumbColor = VyoraTextSecondary,
                uncheckedTrackColor = VyoraNavyHighlight
            )
        )
    }
}
