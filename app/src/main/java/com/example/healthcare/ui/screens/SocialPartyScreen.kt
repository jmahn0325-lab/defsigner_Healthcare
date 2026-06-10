package com.example.healthcare.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcare.data.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialPartyScreen(myUid: String, onBack: () -> Unit, onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository() }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Party?>(null) }
    var selectedMember by remember { mutableStateOf<UserScore?>(null) }
    var viewingParty by remember { mutableStateOf<Party?>(null) }
    
    var partyNameInput by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var editNameInput by remember { mutableStateOf("") }
    
    // 실시간 파티 목록 구독
    val myParties by repository.getMyPartiesFlow(myUid).collectAsState(initial = emptyList())
    var leaderboard by remember { mutableStateOf<List<UserScore>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Log.d("SocialPartyScreen", "Initial load with myUid: $myUid")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (viewingParty != null) viewingParty!!.partyName else "소셜 파티", 
                        fontWeight = FontWeight.Bold 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewingParty != null) {
                            viewingParty = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (viewingParty == null) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "파티 생성")
                        }
                        IconButton(onClick = { showJoinDialog = true }) {
                            Icon(Icons.Default.Group, contentDescription = "파티 참여")
                        }
                        IconButton(onClick = onNavigateToChat) {
                            Icon(Icons.Default.Chat, contentDescription = "공용 채팅")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewingParty == null) {
                // 내 파티 목록 보기
                if (myParties.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("참여 중인 파티가 없습니다.", color = Color.Gray)
                        Text("친구를 초대하거나 코드를 입력해 참여하세요!", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Text("나의 파티 목록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(myParties) { _, party ->
                            PartyItem(
                                party = party,
                                onEdit = { 
                                    showEditDialog = party
                                    editNameInput = party.partyName
                                },
                                onClick = {
                                    viewingParty = party
                                    scope.launch {
                                        isLoading = true
                                        leaderboard = repository.getPartyLeaderboard(party.partyId)
                                        isLoading = false
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // 특정 파티의 리더보드 보기
                if (leaderboard.isEmpty()) {
                    Text("멤버 정보를 가져올 수 없습니다.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Text("리더보드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        itemsIndexed(leaderboard) { index, member ->
                            LeaderboardItem(
                                rank = index + 1,
                                member = member,
                                isMe = member.uid == myUid,
                                onClick = { selectedMember = member }
                            )
                        }
                    }
                }
            }
        }
    }

    // 파티 생성 다이얼로그
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("새 파티 생성") },
            text = {
                OutlinedTextField(
                    value = partyNameInput,
                    onValueChange = { partyNameInput = it },
                    label = { Text("파티 이름") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val code = repository.createParty(partyNameInput, myUid)
                        if (code != "ERROR") {
                            Toast.makeText(context, "파티 생성 완료! 초대 코드: $code", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "파티 생성에 실패했습니다. Firebase 설정을 확인해주세요.", Toast.LENGTH_LONG).show()
                        }
                        showCreateDialog = false
                    }
                }) { Text("생성") }
            }
        )
    }

    // 파티 참여 다이얼로그
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("파티 참여하기") },
            text = {
                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = { inviteCodeInput = it },
                    label = { Text("6자리 초대 코드") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val success = repository.joinParty(inviteCodeInput, myUid)
                        if (success) {
                            Toast.makeText(context, "파티에 참여했습니다!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "잘못된 코드이거나 참여할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                        showJoinDialog = false
                    }
                }) { Text("참여") }
            }
        )
    }

    // 파티 이름 수정 다이얼로그
    if (showEditDialog != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("파티 이름 수정") },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("새로운 이름") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val success = repository.updatePartyName(showEditDialog!!.partyId, editNameInput)
                        if (success) {
                            Toast.makeText(context, "이름이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        showEditDialog = null
                    }
                }) { Text("수정") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) { Text("취소") }
            }
        )
    }

    // 멤버 상세 정보 다이얼로그 (콕 찌르기 포함)
    if (selectedMember != null) {
        MemberDetailDialog(
            member = selectedMember!!,
            repository = repository,
            myUid = myUid,
            onDismiss = { selectedMember = null }
        )
    }
}

@Composable
fun PartyItem(party: Party, onEdit: () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = party.partyName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "이름 수정", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${party.memberUids.size}명",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "초대 코드: ${party.inviteCode}", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun LeaderboardItem(rank: Int, member: UserScore, isMe: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rank.toString(),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.displayName, fontWeight = FontWeight.Bold)
                if (member.bio.isNotBlank()) {
                    Text(text = member.bio, fontSize = 11.sp, color = Color.Gray)
                }
                if (isMe) Text("(나)", fontSize = 10.sp, color = Color.Gray)
            }
            Text(
                text = if (member.isPublic || isMe) "${member.totalScore}점" else "비공개",
                fontWeight = FontWeight.Bold,
                color = if (member.isPublic || isMe) MaterialTheme.colorScheme.secondary else Color.Gray
            )
        }
    }
}

@Composable
fun MemberDetailDialog(
    member: UserScore,
    repository: SocialRepository,
    myUid: String,
    onDismiss: () -> Unit
) {
    var details by remember { mutableStateOf<MemberDetails?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(member.uid) {
        details = repository.getMemberDetailLogs(member.uid)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.displayName}님의 건강 로그") },
        text = {
            if (details == null) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (details!!.scores.isEmpty()) {
                        Text("비공개 항목이거나 기록이 없습니다.", color = Color.Gray, fontSize = 12.sp)
                    }
                    
                    // 알코올, 카페인, 흡연 순서로 정렬하여 표시
                    val displayOrder = listOf("알코올", "카페인", "흡연")
                    displayOrder.forEach { factor ->
                        if (details!!.scores.containsKey(factor)) {
                            val score = details!!.scores[factor] ?: 0f
                            val logs = details!!.detailedLogs[factor] ?: emptyList()
                            
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val categoryEmoji = when(factor) {
                                            "알코올" -> "🍺 "
                                            "카페인" -> "☕ "
                                            "흡연" -> "🚬 "
                                            else -> ""
                                        }
                                        Text(text = "$categoryEmoji$factor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        
                                        val unitLabel = when(factor) {
                                            "알코올" -> "g"
                                            "카페인" -> "mg"
                                            "흡연" -> "개비"
                                            else -> ""
                                        }
                                        
                                        val intake24h = details!!.totalIntake24h[factor] ?: 0f
                                        
                                        Text(
                                            text = "총 감점: ${String.format(Locale.getDefault(), "%.2f", score)}점 / 24시간 섭취량: ${intake24h.toInt()}$unitLabel", 
                                            fontSize = 12.sp, 
                                            color = Color.Gray
                                        )
                                    }
                                    if (member.uid != myUid) {
                                        Row {
                                            IconButton(onClick = {
                                                Toast.makeText(context, "${member.displayName}님에게 쪽지를 보냈습니다.", Toast.LENGTH_SHORT).show()
                                                scope.launch {
                                                    // 요구사항 2: mailbox 컬렉션에 저장
                                                    repository.sendPrivateMail(
                                                        senderId = myUid,
                                                        receiverId = member.uid,
                                                        title = "콕 찌르기 ($factor)",
                                                        body = "${factor} 섭취가 감지되었습니다. 주의하세요!",
                                                        action = "OPEN_CHAT"
                                                    )
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.Chat,
                                                    contentDescription = "쪽지 보내기",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(onClick = {
                                                // 기존 콕 찌르기 (Interactions/Notifications)
                                                Toast.makeText(context, "${member.displayName}님에게 콕 찌르기(${factor})를 했습니다", Toast.LENGTH_SHORT).show()
                                                scope.launch {
                                                    repository.sendNudge(myUid, member.uid, factor)
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.NotificationsActive,
                                                    contentDescription = "콕 찌르기",
                                                    tint = if (score < -10) Color.Red else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // 상세 로그 표시 (최근 7일 통합, 스크롤 가능)
                                if (logs.isNotEmpty()) {
                                    val logScrollState = rememberScrollState()
                                    val maxHeight = if (factor == "흡연") 300.dp else 160.dp
                                    Surface(
                                        color = Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
                                    ) {
                                        Box {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(logScrollState)
                                                    .padding(12.dp)
                                            ) {
                                                logs.forEach { log ->
                                                    Text(
                                                        text = "• $log",
                                                        fontSize = 12.sp,
                                                        color = Color.DarkGray,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            
                                            // 스크롤바 가시화
                                            if (logScrollState.maxValue > 0) {
                                                val scrollFraction = logScrollState.value.toFloat() / logScrollState.maxValue
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 2.dp, end = 2.dp, bottom = 2.dp)
                                                        .width(3.dp)
                                                        .fillMaxHeight()
                                                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                                                ) {
                                                    val thumbHeightFraction = (maxHeight.value / (maxHeight.value + (logScrollState.maxValue / 2.5f))).coerceIn(0.1f, 1f)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .fillMaxHeight(thumbHeightFraction)
                                                            .align(Alignment.TopStart)
                                                            .offset(y = (maxHeight.value * (1 - thumbHeightFraction) * scrollFraction).dp)
                                                            .background(Color.Gray.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
