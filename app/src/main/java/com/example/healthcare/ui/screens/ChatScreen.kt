package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.ChatMessage
import com.example.healthcare.data.SocialRepository
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val healthState = remember { com.example.healthcare.data.HealthState.getInstance(context) }
    val repository = remember { SocialRepository() }
    val myUid = healthState.userId
    
    // 앱 실행 시점 (요구사항 3: 실행 시점 이후 메시지만 가져오기)
    val appStartTime = remember { Date() }
    
    val messages by repository.getRealtimeMessagesFlow(appStartTime)
        .collectAsState(initial = emptyList())
    
    var textState by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("실시간 공용 채팅") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    ChatBubble(message, isMe = message.senderId == myUid)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요...") }
                )
                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            val msg = textState
                            textState = ""
                            // 메시지 전송 로직
                            kotlinx.coroutines.MainScope().launch {
                                repository.sendMessage(myUid, msg)
                            }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isMe: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = if (isMe) "나" else message.senderId.take(5),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// 헬퍼 extension
private fun kotlinx.coroutines.CoroutineScope.launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    this.launch(block = block)
}
