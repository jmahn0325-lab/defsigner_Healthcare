package com.example.healthcare.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.example.healthcare.data.HealthState
import com.example.healthcare.data.SocialRepository
import com.example.healthcare.ui.components.*
import com.example.healthcare.utils.*
import com.example.healthcare.widget.HealthWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHealthSpectrumScreen(
    healthState: HealthState,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSocial: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isApiSyncing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val healthConnectClient = remember {
        val availabilityStatus = HealthConnectClient.getSdkStatus(context)
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }
    
    val repository = remember { SocialRepository() }

    // 메인 화면 진입 시 점수 서버 동기화
    LaunchedEffect(healthState.getHealthScore()) {
        repository.updateUserScore(healthState.userId, healthState.getHealthScore())
    }

    val healthPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class), // 활동시간 계산을 위해 필요함
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthPermissions)) {
            coroutineScope.launch {
                isApiSyncing = true
                if (healthConnectClient != null) {
                    val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                    sleepHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "수면", value) }

                    val activeTimeHistory = fetchHistoricalActiveTime(healthConnectClient, 35)
                    activeTimeHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "활동시간", value) }
                }
                val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                screenTimeHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "스크린 타임", value) }

                HealthWidget.updateAllWidgets(context)
                isApiSyncing = false
            }
        } else {
            Toast.makeText(context, "건강 데이터 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "안녕하세요!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "오늘의 건강 요약",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSocial) {
                        Icon(imageVector = Icons.Default.Group, contentDescription = "소셜 파티")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "설정")
                    }
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
                                    val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                                    sleepHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "수면", value) }
                                    val activeTimeHistory = fetchHistoricalActiveTime(healthConnectClient, 35)
                                    activeTimeHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "활동시간", value) }
                                    val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                                    screenTimeHistory.forEach { (date: LocalDate, value: Float) -> healthState.updateAutoRecord(date, "스크린 타임", value) }
                                    
                                    HealthWidget.updateAllWidgets(context)
                                    isApiSyncing = false
                                } else {
                                    permissionLauncher.launch(healthPermissions)
                                }
                            }
                        }
                    ) {
                        if (isApiSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "동기화")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopSpectrumBanner(
                score = healthState.getHealthScore(),
                message = healthState.getHealthFeedback(),
                onClick = { onNavigateToDetail("종합 점수") }
            )

            Text(
                text = "직접 기록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val alcoholCups = healthState.getTodayValue("알코올") / healthState.selectedAlcoholType.content
                    HealthInputSlider("🍶", "알코올", healthState.selectedAlcoholType.unit, value = alcoholCups, maxValue = healthState.alcoholTarget,
                        onClick = { onNavigateToDetail("알코올") })
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val caffeineCups = healthState.getTodayValue("카페인") / healthState.selectedCaffeineType.content
                    HealthInputSlider("☕", "카페인", healthState.selectedCaffeineType.unit, value = caffeineCups, maxValue = healthState.caffeineTarget,
                        onClick = { onNavigateToDetail("카페인") })
                }
            }
            HealthInputSlider("🚬", "흡연", "개비", value = healthState.getTodayValue("흡연"), maxValue = healthState.smokingTarget,
                onClick = { onNavigateToDetail("흡연") })

            Text(
                text = "자동 측정 데이터",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val activeTime = healthState.getTodayValue("활동시간")
                    val hours = activeTime.toInt()
                    val minutes = ((activeTime - hours) * 60).roundToInt()
                    val activeDisplay = when {
                        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
                        hours > 0 -> "${hours}시간"
                        else -> "${minutes}분"
                    }
                    HealthApiRecord("🧍", "활동시간", activeDisplay, progress = activeTime / healthState.activityTarget.coerceAtLeast(0.1f), Color(0xFFFF9800), onClick = { onNavigateToDetail("활동시간") })
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HealthApiRecord("🌙", "수면", String.format(Locale.getDefault(), "%.1f시간", healthState.getTodayValue("수면")), progress = healthState.getTodayValue("수면") / healthState.sleepTarget.coerceAtLeast(1f), Color(0xFF673AB7), onClick = { onNavigateToDetail("수면") })
                }
            }

            HealthApiRecord("📱", "스크린 타임", String.format(Locale.getDefault(), "%.1f시간", healthState.getTodayValue("스크린 타임")), progress = healthState.getTodayValue("스크린 타임") / healthState.screenTimeTarget.coerceAtLeast(1f), Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") })

            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}