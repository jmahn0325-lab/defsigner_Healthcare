package com.example.healthcare.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcare.data.HealthState
import com.example.healthcare.data.SocialRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(healthState: HealthState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository() }
    
    var nameInput by remember { mutableStateOf(healthState.userName) }
    var bioInput by remember { mutableStateOf(healthState.bio) }
    var isPublicToggle by remember { mutableStateOf(healthState.isPublic) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필 설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 이름 수정
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("이름(별명)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 소개글 수정
            OutlinedTextField(
                value = bioInput,
                onValueChange = { bioInput = it },
                label = { Text("한 줄 소개") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("나를 표현해 보세요 (예: 건강을 위해 노력 중!)") }
            )

            // 공개 여부 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("파티 내 정보 공개", fontWeight = FontWeight.Bold)
                    Text(
                        "비공개 시 파티 리더보드에서 내 점수가 '비공개'로 표시됩니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPublicToggle,
                    onCheckedChange = { isPublicToggle = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (nameInput.isBlank()) {
                        Toast.makeText(context, "이름은 비워둘 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    scope.launch {
                        isSaving = true
                        val success = repository.updateUserProfile(
                            uid = healthState.userId,
                            name = nameInput,
                            bio = bioInput,
                            isPublic = isPublicToggle
                        )
                        if (success) {
                            healthState.userName = nameInput
                            healthState.bio = bioInput
                            healthState.isPublic = isPublicToggle
                            healthState.saveProfile()
                            Toast.makeText(context, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "저장에 실패했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("저장하기")
                }
            }
        }
    }
}
