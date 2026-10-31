package com.vyora.feature.onboarding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VyoraNavyDark)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VyoraEmergencyOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = VyoraNavyDark)
                }
                Column {
                    Text("VYORA", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, color = VyoraTextPrimary)
                    Text("OFFLINE TACTICAL MESH", fontSize = 11.sp, letterSpacing = 1.sp, color = VyoraCyanSecondary)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("Initialize Terminal Identity", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "VYORA operates completely offline. Your node is addressed using a cryptographic unique ID without centralized accounts, phone numbers, or cloud servers.",
                fontSize = 14.sp,
                color = VyoraTextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
            IdentityCard(
                userId = uiState.generatedUserId,
                onRegenerate = { viewModel.regenerateUserId() },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(uiState.generatedUserId))
                    Toast.makeText(context, "Node ID Copied", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = uiState.nickname,
                onValueChange = { viewModel.onNicknameChanged(it) },
                label = { Text("Radio Call Sign / Nickname") },
                placeholder = { Text("e.g. Echo-Lead, Sarah-Medic") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Button(
            onClick = { viewModel.completeOnboarding(onNavigateToHome) },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ACTIVATE RADAR TERMINAL", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}
