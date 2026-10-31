package com.vyora.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.VyoraNavyDark
import com.vyora.core.designsystem.theme.VyoraTextPrimary
import com.vyora.core.designsystem.theme.VyoraTextSecondary

@Composable
fun DiagnosticsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(VyoraNavyDark).padding(16.dp).statusBarsPadding()
    ) {
        Text("RF Diagnostics & Telemetry", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Active radio link quality, SNR, RSSI levels, and packet routing diagnostics", fontSize = 12.sp, color = VyoraTextSecondary)
    }
}
