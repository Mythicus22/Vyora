package com.vyora.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*

@Composable
fun VyoraBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VyoraCyanSecondary,
    backgroundColor: Color = VyoraNavyHighlight
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}
