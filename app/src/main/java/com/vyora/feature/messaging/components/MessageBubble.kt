package com.vyora.feature.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*
import com.vyora.domain.model.Message
import com.vyora.domain.model.MessageDeliveryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(msg: Message) {
    val isMe = msg.isFromMe
    val timeFormatted = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMe) VyoraNavyHighlight else VyoraNavyCard)
                .border(1.dp, if (isMe) VyoraCyanSecondary.copy(alpha = 0.3f) else VyoraNavyBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(text = msg.text, fontSize = 14.sp, color = VyoraTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = timeFormatted, fontSize = 10.sp, color = VyoraTextSecondary)
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when (msg.status) {
                        MessageDeliveryStatus.QUEUED_LOCALLY,
                        MessageDeliveryStatus.WAITING_FOR_ROUTE -> Icon(Icons.Default.Schedule, null, tint = VyoraTextSecondary, modifier = Modifier.size(12.dp))
                        MessageDeliveryStatus.SENT_TO_RELAY -> Icon(Icons.Default.Check, null, tint = VyoraCyanSecondary, modifier = Modifier.size(12.dp))
                        MessageDeliveryStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, tint = VyoraMeshGreen, modifier = Modifier.size(14.dp))
                        else -> {}
                    }
                }
            }
        }
    }
}
