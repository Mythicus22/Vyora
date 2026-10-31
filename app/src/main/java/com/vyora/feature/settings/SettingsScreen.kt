package com.vyora.feature.settings

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
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(VyoraNavyDark).padding(16.dp).statusBarsPadding()
    ) {
        Text("Node Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("RF modulation, frequency bands, identity reset, and local storage", fontSize = 12.sp, color = VyoraTextSecondary)
    }
}
