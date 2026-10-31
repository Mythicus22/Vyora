package com.vyora.feature.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*

@Composable
fun IdentityCard(
    userId: String,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VyoraNavyCard)
            .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "ASSIGNED UNIQUE USER ID",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = VyoraCyanLight,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = userId,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VyoraTextPrimary
            )
            Row {
                IconButton(onClick = onRegenerate) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Regenerate", tint = VyoraCyanSecondary)
                }
                IconButton(onClick = onCopy) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = VyoraCyanSecondary)
                }
            }
        }
    }
}
