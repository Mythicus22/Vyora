package com.vyora.feature.onboarding

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vyora.core.designsystem.theme.*
import com.vyora.core.permissions.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val allRequiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            PermissionUtils.REQUIRED_LOCATION_PERMISSIONS
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            PermissionUtils.REQUIRED_LOCATION_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasLocationPermission = PermissionUtils.REQUIRED_LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero Logo & Title
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VyoraNavyContainer)
                .border(1.5.dp, VyoraCyanSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = "VYORA Mesh",
                tint = VyoraCyanSecondary,
                modifier = Modifier.size(34.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome to VYORA",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = VyoraTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Off-Grid Tactical Radio Mesh & Offline AI Assistant",
                fontSize = 13.sp,
                color = VyoraCyanLight
            )
        }

        // Permanent Unique Node ID Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VyoraNavyCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(VyoraNavyBorder))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isIdReadFromEsp) "VERIFIED HARDWARE NODE ID (ESP32)" else if (uiState.hasStoredId) "SAVED NODE ID (DATABASE)" else "MESH HARDWARE IDENTITY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isIdReadFromEsp) VyoraMeshGreen else if (uiState.hasStoredId) VyoraMeshGreenDim else VyoraCyanSecondary,
                        letterSpacing = 1.sp
                    )
                    if (uiState.generatedUserId.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VyoraNavyHighlight)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(uiState.generatedUserId))
                                    Toast.makeText(context, "ID copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = VyoraCyanLight, modifier = Modifier.size(12.dp))
                                Text("COPY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VyoraCyanLight)
                            }
                        }
                    }
                }

                Text(
                    text = uiState.generatedUserId.ifEmpty { "Awaiting ESP32 MAC ID..." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.generatedUserId.isNotEmpty()) VyoraTextPrimary else VyoraTextSecondary
                )

                // Serial Hardware ID Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (uiState.hasStoredId) VyoraMeshGreen.copy(alpha = 0.15f)
                            else if (uiState.isEspConnected) VyoraCyanSecondary.copy(alpha = 0.15f)
                            else VyoraNavyHighlight
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.hasStoredId) Icons.Default.CheckCircle
                        else if (uiState.isEspConnected) Icons.Default.Usb
                        else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (uiState.hasStoredId) VyoraMeshGreen
                        else if (uiState.isEspConnected) VyoraCyanSecondary
                        else VyoraCyanLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            uiState.isIdReadFromEsp -> "VERIFIED HARDWARE ID (Read via ESP Serial)"
                            uiState.hasStoredId -> "ID SAVED IN DATABASE • ESP re-read NOT required"
                            uiState.isEspConnected -> "ESP32 CONNECTED • Tap below to read ID"
                            else -> "STANDALONE TERMINAL • Plug ESP OTG to sync"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isIdReadFromEsp || uiState.hasStoredId) VyoraMeshGreen
                        else if (uiState.isEspConnected) VyoraCyanSecondary
                        else VyoraCyanLight
                    )
                }

                if (!uiState.espMacAddress.isNullOrEmpty()) {
                    Text(
                        text = "ESP MAC: ${uiState.espMacAddress} • Node ID: ${uiState.espNodeId ?: ""}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = VyoraCyanLight
                    )
                }

                // Query ESP Serial Action (Only necessary if no ID is stored yet, but kept available if user wants to refresh)
                OutlinedButton(
                    onClick = {
                        viewModel.queryEspHardwareNodeId()
                        Toast.makeText(context, "Querying ESP32 serial (cmd: status)...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VyoraCyanSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VyoraNavyBorder),
                    enabled = !uiState.isQueryingSerial
                ) {
                    if (uiState.isQueryingSerial) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VyoraCyanSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Serial...", fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.hasStoredId) "Re-sync from ESP Serial (Optional)" else "Read Hardware ID from ESP (OTG)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = if (uiState.hasStoredId) {
                        "Your unique ID is permanently stored in Android. You can proceed directly after granting permissions."
                    } else {
                        "Connect your ESP32 node via USB OTG. The permanent unique ID will be fetched from the ESP32 hardware and saved locally."
                    },
                    fontSize = 12.sp,
                    color = VyoraTextSecondary
                )

                Spacer(modifier = Modifier.height(2.dp))

                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = { viewModel.onNicknameChanged(it) },
                    label = { Text("Callsign / Nickname") },
                    placeholder = { Text("e.g. Echo-1, FieldMedic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyoraCyanSecondary,
                        unfocusedBorderColor = VyoraNavyBorder,
                        focusedTextColor = VyoraTextPrimary,
                        unfocusedTextColor = VyoraTextPrimary
                    )
                )
            }
        }

        // Required Permissions Initialization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VyoraNavyCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(VyoraNavyBorder))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REQUIRED PERMISSIONS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VyoraCyanSecondary,
                        letterSpacing = 1.sp
                    )

                    if (!hasLocationPermission || !hasNotificationPermission) {
                        TextButton(
                            onClick = { permissionLauncher.launch(allRequiredPermissions) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Grant All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyoraCyanSecondary)
                        }
                    }
                }

                // Location GPS Permission Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasLocationPermission) VyoraMeshGreen.copy(alpha = 0.15f) else VyoraEmergencyOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (hasLocationPermission) VyoraMeshGreen else VyoraEmergencyOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text("GPS Location", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VyoraTextPrimary)
                            Text("Locates alive mesh nodes & shares emergency position", fontSize = 11.sp, color = VyoraTextSecondary)
                        }
                    }

                    if (hasLocationPermission) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Granted", tint = VyoraMeshGreen, modifier = Modifier.size(24.dp))
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(PermissionUtils.REQUIRED_LOCATION_PERMISSIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = VyoraNavyBorder, thickness = 0.5.dp)

                // Push Notifications Permission Item (Android 13+)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasNotificationPermission) VyoraMeshGreen.copy(alpha = 0.15f) else VyoraEmergencyOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (hasNotificationPermission) VyoraMeshGreen else VyoraEmergencyOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text("Emergency Alerts & Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VyoraTextPrimary)
                            Text("Receives high-priority SOS alerts & incoming mesh packets", fontSize = 11.sp, color = VyoraTextSecondary)
                        }
                    }

                    if (hasNotificationPermission) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Granted", tint = VyoraMeshGreen, modifier = Modifier.size(24.dp))
                    } else {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = VyoraNavyBorder, thickness = 0.5.dp)

                // USB Serial OTG Device Access Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VyoraCyanSecondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = VyoraCyanSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ESP32 Radio Transceiver", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VyoraTextPrimary)
                        Text(
                            if (uiState.hasStoredId) "Hardware link optional on startup (plug OTG anytime for live RF)"
                            else "Plug via USB OTG adapter to read hardware MAC ID",
                            fontSize = 11.sp,
                            color = VyoraTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        val canProceed = uiState.generatedUserId.isNotEmpty() || uiState.hasStoredId

        // Get Started Action Button
        Button(
            onClick = {
                if (!hasLocationPermission || !hasNotificationPermission) {
                    permissionLauncher.launch(allRequiredPermissions)
                }
                if (canProceed) {
                    viewModel.completeOnboarding {
                        onNavigateToHome()
                    }
                }
            },
            enabled = canProceed || uiState.isEspConnected,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VyoraCyanSecondary,
                contentColor = VyoraNavyDark,
                disabledContainerColor = VyoraNavyHighlight,
                disabledContentColor = VyoraTextTertiary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = when {
                    !canProceed -> "Connect ESP32 OTG to Fetch ID"
                    !hasLocationPermission || !hasNotificationPermission -> "Grant Permissions & Enter Mesh"
                    uiState.hasStoredId -> "Proceed to Mesh Map"
                    else -> "Enter VYORA Terminal"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
