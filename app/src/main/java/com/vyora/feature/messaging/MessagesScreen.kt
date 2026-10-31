package com.vyora.feature.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*
import com.vyora.domain.model.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagingViewModel,
    onOpenChat: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VyoraNavyDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = VyoraCyanSecondary,
                contentColor = VyoraNavyDark
            ) {
                Icon(imageVector = Icons.Default.AddComment, contentDescription = "New Conversation")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Mesh Communications", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                Text("Store-and-forward peer addressed channels", fontSize = 12.sp, color = VyoraCyanLight)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.conversations) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VyoraNavyCard)
                            .border(1.dp, VyoraNavyBorder, RoundedCornerShape(12.dp))
                            .clickable { onOpenChat(conv.recipientId) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(VyoraNavyHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(conv.recipientNickname.take(2).uppercase(), fontWeight = FontWeight.Bold, color = VyoraCyanSecondary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conv.recipientNickname, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VyoraTextPrimary)
                            Text("ID: ${conv.recipientId}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = VyoraCyanLight)
                            Text(conv.lastMessageText, fontSize = 13.sp, color = VyoraTextSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (showDialog) {
            var inputId by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = VyoraNavyCard,
                title = { Text("Start Mesh Conversation", color = VyoraTextPrimary) },
                text = {
                    OutlinedTextField(
                        value = inputId,
                        onValueChange = { inputId = it.uppercase() },
                        placeholder = { Text("VYO-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = inputId.trim()
                            if (clean.isNotEmpty()) {
                                showDialog = false
                                onOpenChat(clean)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VyoraCyanSecondary, contentColor = VyoraNavyDark)
                    ) {
                        Text("Create Channel")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel", color = VyoraTextSecondary) }
                }
            )
        }
    }
}
