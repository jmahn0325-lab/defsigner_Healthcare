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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 네비게이션을 관리하는 최상위 앱 컴포저블 실행
                    HealthApp()
                }
            }
        }
    }
}

// ==========================================
// 네비게이션 설정 화면
// ==========================================
@Composable
fun HealthApp() {
    val navController = rememberNavController()

    // NavHost로 화면 전환 경로를 정의합니다.
    NavHost(navController = navController, startDestination = "main") {
        // 1. 메인 화면
        composable("main") {
            MainHealthSpectrumScreen(
                onNavigateToDetail = { itemName ->
                    // 항목 이름(itemName)을 경로에 담아서 상세 화면으로 이동
                    navController.navigate("detail/$itemName")
                }
            )
        }
        // 2. 상세 화면
        composable("detail/{itemName}") { backStackEntry ->
            val itemName = backStackEntry.arguments?.getString("itemName") ?: "상세"
            DetailScreen(
                itemName = itemName,
                onBack = { navController.popBackStack() } // 뒤로 가기 동작
            )
        }
    }
}

// ==========================================
// 메인 화면
// ==========================================
@Composable
fun MainHealthSpectrumScreen(onNavigateToDetail: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isApiSyncing by remember { mutableStateOf(value = false) }
    var sleepHours by remember { mutableFloatStateOf(0f) }
    var steps by remember { mutableFloatStateOf(0f) }
    var standHours by remember { mutableFloatStateOf(0f) }
    var screenTime by remember { mutableFloatStateOf(0f) }

    val healthConnectClient = remember {
        val availabilityStatus = HealthConnectClient.getSdkStatus(context)
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
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
                    steps = fetchSteps(healthConnectClient)
                    sleepHours = fetchSleep(healthConnectClient)
                }
                screenTime = fetchScreenTime(context)
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
        // 상단 배너 클릭 시 "종합 점수" 상세로 이동
        TopSpectrumBanner(onClick = { onNavigateToDetail("종합 점수") })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isApiSyncing) "데이터 불러오는 중..." else "데이터 동기화",
                fontSize = 12.sp,
                color = Color.Gray
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
                            steps = fetchSteps(healthConnectClient)
                            sleepHours = fetchSleep(healthConnectClient)
                            screenTime = fetchScreenTime(context)
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
                // 클릭 시 각각의 이름으로 상세 화면 이동 요청
                HealthInputSlider("🍶", "알코올", "잔", 2f, 10f, onClick = { onNavigateToDetail("알코올") })
                HealthInputSlider("🚬", "흡연", "개비", 6f, 20f, onClick = { onNavigateToDetail("흡연") })
                HealthInputSlider("☕", "카페인", "잔", 2f, 10f, onClick = { onNavigateToDetail("카페인") })
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HealthApiRecord(
                    "🌙", "수면", String.format(java.util.Locale.getDefault(), "%.1f시간", sleepHours),
                    progress = sleepHours / 12f, Color(0xFF673AB7), onClick = { onNavigateToDetail("수면") }
                )
                HealthApiRecord(
                    "👣", "걸음수", "${steps.toInt()}보",
                    progress = steps / 10000f, Color(0xFF4CAF50), onClick = { onNavigateToDetail("걸음수") }
                )
                HealthApiRecord(
                    "🧍", "일어서기", "${standHours.toInt()}시간",
                    progress = standHours / 12f, Color(0xFFFF9800), onClick = { onNavigateToDetail("일어서기") }
                )
                HealthApiRecord(
                    "📱", "스크린 타임", String.format(java.util.Locale.getDefault(), "%.1f시간", screenTime),
                    progress = screenTime / 24f, Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}




// ==========================================
// UI 컴포넌트 모음 (클릭 이벤트 적용)
// ==========================================

@Composable
fun TopSpectrumBanner(onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .clickable { onClick() } // 클릭 가능하도록 수정
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(1.dp, Color.Black, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "😏", fontSize = 32.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "70", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .padding(bottom = 6.dp),
                        color = Color.Blue,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "100", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    text = "수면은 충분하지만, 어제 음주를 많이 하셨네요.\n술을 줄이고 물을 많이 마셔볼까요?",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "상세 정보",
                modifier = Modifier.align(Alignment.Top),
            )
        }
    }
}

@Composable
fun HealthInputSlider(emoji: String, title: String, valueSuffix: String, initialValue: Float, maxValue: Float, onClick: () -> Unit = {}) {
    var sliderValue by rememberSaveable { mutableFloatStateOf(initialValue) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray)
            .clickable { onClick() } // 클릭 가능하도록 수정 (padding보다 위에 있어야 Box 전체 터치됨)
            .padding(12.dp),
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
                Text(text = "${sliderValue.toInt()}$valueSuffix", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..maxValue,
                modifier = Modifier.height(24.dp),
            )
        }
    }
}

@Composable
fun HealthApiRecord(emoji: String, title: String, value: String, progress: Float, color: Color, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray)
            .clickable { onClick() } // 클릭 가능하도록 수정
            .padding(12.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .border(1.dp, Color.LightGray),
                color = color,
                trackColor = Color.Transparent,
            )
        }
    }
}


// ==========================================
// API 데이터 패치(가져오기) 함수 모음 (변경 없음)
// ==========================================
suspend fun fetchSteps(client: HealthConnectClient): Float {
    return try {
        val now = Instant.now()
        val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            ),
        )
        response.records.sumOf { it.count }.toFloat()
    } catch (e: Exception) {
        e.printStackTrace()
        0f
    }
}

suspend fun fetchSleep(client: HealthConnectClient): Float {
    return try {
        val now = Instant.now()
        val yesterdayEvening = ZonedDateTime.now().minusDays(1).withHour(18).truncatedTo(ChronoUnit.HOURS).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(yesterdayEvening, now),
            ),
        )
        val totalMinutes = response.records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
        totalMinutes / 60f
    } catch (e: Exception) {
        e.printStackTrace()
        0f
    }
}

fun fetchScreenTime(context: Context): Float {
    return try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDayMilli = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
        val nowMilli = System.currentTimeMillis()

        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startOfDayMilli, nowMilli
        )
        val totalTimeInForeground = queryUsageStats.sumOf { it.totalTimeInForeground }
        (totalTimeInForeground.toFloat() / (1000f * 60f * 60f))
    } catch (e: Exception) {
        e.printStackTrace()
        0f
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}