package com.example.healthcare.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
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

@Composable
fun UserNameSettingScreen(healthState: HealthState, onComplete: () -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository() }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "반가워요!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "당신의 건강 별명을 정해주세요!",
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("별명 (예: 건강왕)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "성별을 선택해 주세요",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "성별에 따라 맞춤형 건강 기준이 적용됩니다.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedGender == "Male",
                onClick = { selectedGender = "Male" },
                enabled = !isLoading
            )
            Text(text = "남성")
            Spacer(modifier = Modifier.width(24.dp))
            RadioButton(
                selected = selectedGender == "Female",
                onClick = { selectedGender = "Female" },
                enabled = !isLoading
            )
            Text(text = "여성")
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (nameInput.isBlank()) {
                    Toast.makeText(context, "별명을 입력해주세요!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                scope.launch {
                    isLoading = true
                    val success = repository.createUser(healthState.userId, nameInput, selectedGender)
                    if (success) {
                        healthState.userName = nameInput
                        healthState.gender = selectedGender
                        healthState.isOnboardingCompleted = true
                        healthState.saveProfile()
                        onComplete()
                    } else {
                        Toast.makeText(context, "유저 등록에 실패했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("시작하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
