package com.example.healthcare.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.example.healthcare.data.HealthState
import com.example.healthcare.ui.components.*
import com.example.healthcare.utils.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MainHealthSpectrumScreen(healthState: HealthState, onNavigateToDetail: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isApiSyncing by remember { mutableStateOf(false) }

    val healthConnectClient = remember {
        val availabilityStatus = HealthConnectClient.getSdkStatus(context)
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthPermissions)) {
            coroutineScope.launch {
                isApiSyncing = true
                if (healthConnectClient != null) {
                    val stepsHistory = fetchHistoricalSteps(healthConnectClient, 35)
                    stepsHistory.forEach { (date, value) -> healthState.updateRecord(date, "걸음수", value) }

                    val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                    sleepHistory.forEach { (date, value) -> healthState.updateRecord(date, "수면", value) }

                    val activeTimeHistory = fetchHistoricalActiveTime(healthConnectClient, 35)
                    activeTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "일어서기", value) }
                }
                val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                screenTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "스크린 타임", value) }
                isApiSyncing = false
            }
        } else {
            Toast.makeText(context, "건강 데이터 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (healthConnectClient != null) {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(healthPermissions)) {
                isApiSyncing = true
                val stepsHistory = fetchHistoricalSteps(healthConnectClient, 35)
                stepsHistory.forEach { (date, value) -> healthState.updateRecord(date, "걸음수", value) }

                val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                sleepHistory.forEach { (date, value) -> healthState.updateRecord(date, "수면", value) }

                val activeTimeHistory = fetchHistoricalActiveTime(healthConnectClient, 35)
                activeTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "일어서기", value) }

                if (hasUsageStatsPermission(context)) {
                    val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                    screenTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "스크린 타임", value) }
                }
                isApiSyncing = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TopSpectrumBanner(onClick = { onNavigateToDetail("종합 점수") })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (isApiSyncing) "데이터 불러오는 중..." else "데이터 동기화", fontSize = 12.sp, color = Color.Gray)
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (!hasUsageStatsPermission(context)) {
                            Toast.makeText(context, "스크린 타임을 측정하려면 '사용 정보 접근' 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            return@launch
                        }
                        if (healthConnectClient == null) {
                            Toast.makeText(context, "기기에 Health Connect 앱이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val granted = healthConnectClient.permissionController.getGrantedPermissions()
                        if (granted.containsAll(healthPermissions)) {
                            isApiSyncing = true
                            val stepsHistory = fetchHistoricalSteps(healthConnectClient, 35)
                            stepsHistory.forEach { (date, value) -> healthState.updateRecord(date, "걸음수", value) }

                            val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                            sleepHistory.forEach { (date, value) -> healthState.updateRecord(date, "수면", value) }

                            val activeTimeHistory = fetchHistoricalActiveTime(healthConnectClient, 35)
                            activeTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "일어서기", value) }

                            val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                            screenTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "스크린 타임", value) }
                            isApiSyncing = false
                        } else {
                            permissionLauncher.launch(healthPermissions)
                        }
                    }
                }
            ) {
                if (isApiSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "동기화", tint = Color.Gray)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HealthInputSlider("🍶", "알코올", "잔", value = healthState.getTodayValue("알코올"), maxValue = healthState.alcoholTarget, onValueChange = { healthState.updateRecord(LocalDate.now(), "알코올", it) }, onClick = { onNavigateToDetail("알코올") })
                HealthInputSlider("🚬", "흡연", "개비", value = healthState.getTodayValue("흡연"), maxValue = healthState.smokingTarget, onValueChange = { healthState.updateRecord(LocalDate.now(), "흡연", it) }, onClick = { onNavigateToDetail("흡연") })
                HealthInputSlider("☕", "카페인", "잔", value = healthState.getTodayValue("카페인"), maxValue = healthState.caffeineTarget, onValueChange = { healthState.updateRecord(LocalDate.now(), "카페인", it) }, onClick = { onNavigateToDetail("카페인") })
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HealthApiRecord("🌙", "수면", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.getTodayValue("수면")), progress = healthState.getTodayValue("수면") / healthState.sleepTarget.coerceAtLeast(1f), Color(0xFF673AB7), onClick = { onNavigateToDetail("수면") })
                HealthApiRecord("👣", "걸음수", "${healthState.getTodayValue("걸음수").toInt()}보", progress = healthState.getTodayValue("걸음수") / healthState.stepsTarget.coerceAtLeast(1f), Color(0xFF4CAF50), onClick = { onNavigateToDetail("걸음수") })

                val activeTime = healthState.getTodayValue("일어서기")
                val activeDisplay = if (activeTime < 1f && activeTime > 0f) "${(activeTime * 60).toInt()}분" else String.format(java.util.Locale.getDefault(), "%.1f시간", activeTime)
                HealthApiRecord("🧍", "일어서기", activeDisplay, progress = activeTime / healthState.standTarget.coerceAtLeast(1f), Color(0xFFFF9800), onClick = { onNavigateToDetail("일어서기") })

                HealthApiRecord("📱", "스크린 타임", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.getTodayValue("스크린 타임")), progress = healthState.getTodayValue("스크린 타임") / healthState.screenTimeTarget.coerceAtLeast(1f), Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}