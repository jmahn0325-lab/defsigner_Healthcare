package com.example.healthcare

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// ==========================================
// 전체 앱 상태 관리 (목표량 & 현재치)
// ==========================================
class HealthState {
    // 1. 목표량 (DetailScreen에서 설정 -> MainScreen 최대치로 반영)
    var alcoholTarget by mutableFloatStateOf(10f)
    var smokingTarget by mutableFloatStateOf(20f)
    var caffeineTarget by mutableFloatStateOf(10f)
    var sleepTarget by mutableFloatStateOf(8f)
    var stepsTarget by mutableFloatStateOf(10000f)
    var standTarget by mutableFloatStateOf(12f)
    var screenTimeTarget by mutableFloatStateOf(6f)

    // 2. 현재 입력/측정치
    var alcoholCurrent by mutableFloatStateOf(2f)
    var smokingCurrent by mutableFloatStateOf(6f)
    var caffeineCurrent by mutableFloatStateOf(2f)

    // API 기반 자동 측정치
    var sleepCurrent by mutableFloatStateOf(0f)
    var stepsCurrent by mutableFloatStateOf(0f)
    var standCurrent by mutableFloatStateOf(0f)
    var screenTimeCurrent by mutableFloatStateOf(0f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HealthApp()
                }
            }
        }
    }
}

@Composable
fun HealthApp() {
    val navController = rememberNavController()
    // 전체 상태를 최상위에서 생성하여 하위 화면들에 공유합니다.
    val healthState = remember { HealthState() }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainHealthSpectrumScreen(
                healthState = healthState,
                onNavigateToDetail = { itemName ->
                    navController.navigate("detail/$itemName")
                }
            )
        }
        composable("detail/{itemName}") { backStackEntry ->
            val itemName = backStackEntry.arguments?.getString("itemName") ?: "상세"
            DetailScreen(
                itemName = itemName,
                healthState = healthState,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ==========================================
// 메인 화면
// ==========================================
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
                    healthState.stepsCurrent = fetchSteps(healthConnectClient)
                    healthState.sleepCurrent = fetchSleep(healthConnectClient)
                }
                healthState.screenTimeCurrent = fetchScreenTime(context)
                isApiSyncing = false
            }
        } else {
            Toast.makeText(context, "건강 데이터 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TopSpectrumBanner(onClick = { onNavigateToDetail("종합 점수") })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isApiSyncing) "데이터 불러오는 중..." else "데이터 동기화",
                fontSize = 12.sp, color = Color.Gray
            )
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
                            healthState.stepsCurrent = fetchSteps(healthConnectClient)
                            healthState.sleepCurrent = fetchSleep(healthConnectClient)
                            healthState.screenTimeCurrent = fetchScreenTime(context)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HealthInputSlider(
                    "🍶", "알코올", "잔",
                    value = healthState.alcoholCurrent,
                    maxValue = healthState.alcoholTarget, // 목표치를 최대치로 연동
                    onValueChange = { healthState.alcoholCurrent = it },
                    onClick = { onNavigateToDetail("알코올") }
                )
                HealthInputSlider(
                    "🚬", "흡연", "개비",
                    value = healthState.smokingCurrent,
                    maxValue = healthState.smokingTarget,
                    onValueChange = { healthState.smokingCurrent = it },
                    onClick = { onNavigateToDetail("흡연") }
                )
                HealthInputSlider(
                    "☕", "카페인", "잔",
                    value = healthState.caffeineCurrent,
                    maxValue = healthState.caffeineTarget,
                    onValueChange = { healthState.caffeineCurrent = it },
                    onClick = { onNavigateToDetail("카페인") }
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HealthApiRecord(
                    "🌙", "수면", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.sleepCurrent),
                    progress = healthState.sleepCurrent / healthState.sleepTarget.coerceAtLeast(1f),
                    Color(0xFF673AB7), onClick = { onNavigateToDetail("수면") }
                )
                HealthApiRecord(
                    "👣", "걸음수", "${healthState.stepsCurrent.toInt()}보",
                    progress = healthState.stepsCurrent / healthState.stepsTarget.coerceAtLeast(1f),
                    Color(0xFF4CAF50), onClick = { onNavigateToDetail("걸음수") }
                )
                HealthApiRecord(
                    "🧍", "일어서기", "${healthState.standCurrent.toInt()}시간",
                    progress = healthState.standCurrent / healthState.standTarget.coerceAtLeast(1f),
                    Color(0xFFFF9800), onClick = { onNavigateToDetail("일어서기") }
                )
                HealthApiRecord(
                    "📱", "스크린 타임", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.screenTimeCurrent),
                    progress = healthState.screenTimeCurrent / healthState.screenTimeTarget.coerceAtLeast(1f),
                    Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// UI 컴포넌트 모음
// ==========================================
@Composable
fun TopSpectrumBanner(onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black).clickable { onClick() }.padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(60.dp).border(1.dp, Color.Black, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text(text = "😏", fontSize = 32.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "70", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier.weight(1f).height(12.dp).padding(bottom = 6.dp),
                        color = Color.Blue,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "100", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    text = "수면은 충분하지만, 어제 음주를 많이 하셨네요.\n술을 줄이고 물을 많이 마셔볼까요?",
                    fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Icon(imageVector = Icons.Default.Info, contentDescription = "상세 정보", modifier = Modifier.align(Alignment.Top))
        }
    }
}

@Composable
fun HealthInputSlider(emoji: String, title: String, valueSuffix: String, value: Float, maxValue: Float, onValueChange: (Float) -> Unit, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = "${value.toInt()}$valueSuffix", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                // 목표량이 줄어들었을 때 에러가 나지 않도록 예외 처리
                value = value.coerceIn(0f, maxValue.coerceAtLeast(1f)),
                onValueChange = onValueChange,
                valueRange = 0f..maxValue.coerceAtLeast(1f),
                modifier = Modifier.height(24.dp),
            )
            // 메인 화면 슬라이더 하단에 목표(최대)치 표기
            Text(text = "Max: ${maxValue.toInt()}$valueSuffix", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun HealthApiRecord(emoji: String, title: String, value: String, progress: Float, color: Color, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = value, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).border(1.dp, Color.LightGray),
                color = color, trackColor = Color.Transparent,
            )
        }
    }
}

// API 함수들은 변경 없이 그대로 유지
suspend fun fetchSteps(client: HealthConnectClient): Float { return try { val now = Instant.now(); val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant(); val response = client.readRecords(ReadRecordsRequest(recordType = StepsRecord::class, timeRangeFilter = TimeRangeFilter.between(startOfDay, now),)); response.records.sumOf { it.count }.toFloat() } catch (e: Exception) { 0f } }
suspend fun fetchSleep(client: HealthConnectClient): Float { return try { val now = Instant.now(); val yesterdayEvening = ZonedDateTime.now().minusDays(1).withHour(18).truncatedTo(ChronoUnit.HOURS).toInstant(); val response = client.readRecords(ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(yesterdayEvening, now),)); val totalMinutes = response.records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }; totalMinutes / 60f } catch (e: Exception) { 0f } }
fun fetchScreenTime(context: Context): Float { return try { val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager; val startOfDayMilli = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli(); val nowMilli = System.currentTimeMillis(); val queryUsageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayMilli, nowMilli); val totalTimeInForeground = queryUsageStats.sumOf { it.totalTimeInForeground }; (totalTimeInForeground.toFloat() / (1000f * 60f * 60f)) } catch (e: Exception) { 0f } }
fun hasUsageStatsPermission(context: Context): Boolean { val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) { appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) } else { @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) }; return mode == AppOpsManager.MODE_ALLOWED }