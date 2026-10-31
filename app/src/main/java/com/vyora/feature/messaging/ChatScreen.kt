package com.vyora.feature.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyora.core.designsystem.theme.*
import com.vyora.feature.messaging.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    myUserId: String,
    viewModel: MessagingViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(peerId) {
        viewModel.selectConversation(peerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peerId, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VyoraTextPrimary)
                        Text("Sub-GHz Mesh Direct Address", fontSize = 11.sp, color = VyoraCyanSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = VyoraCyanSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VyoraNavyCard)
            )
        },
        containerColor = VyoraNavyDark
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(uiState.currentMessages) { msg ->
                    MessageBubble(msg = msg)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VyoraNavyCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onInputChanged(it) },
                    placeholder = { Text("Type mesh packet...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                IconButton(
                    onClick = { viewModel.sendMessage(myUserId, peerId) },
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(VyoraCyanSecondary)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = VyoraNavyDark)
                }
            }
        }
    }
}
