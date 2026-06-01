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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

// ==========================================
// DB 역할을 하는 상태 관리 클래스 (History & Targets)
// ==========================================
data class HealthRecord(val date: LocalDate, val type: String, val value: Float)

class HealthState {
    var alcoholTarget by mutableFloatStateOf(10f)
    var smokingTarget by mutableFloatStateOf(20f)
    var caffeineTarget by mutableFloatStateOf(10f)
    var sleepTarget by mutableFloatStateOf(8f)
    var stepsTarget by mutableFloatStateOf(10000f)
    var standTarget by mutableFloatStateOf(12f)
    var screenTimeTarget by mutableFloatStateOf(6f)

    private val _records = mutableStateListOf<HealthRecord>()

    init {
        val today = LocalDate.now()
        val manualTypes = listOf("알코올", "흡연", "카페인")
        for (i in 0..35) {
            val date = today.minusDays(i.toLong())
            manualTypes.forEach { type ->
                val value = when(type) {
                    "알코올" -> (0..5).random().toFloat()
                    "흡연" -> (0..10).random().toFloat()
                    "카페인" -> (0..3).random().toFloat()
                    else -> 0f
                }
                _records.add(HealthRecord(date, type, value))
            }
        }
    }

    fun updateRecord(date: LocalDate, type: String, value: Float) {
        _records.removeAll { it.date == date && it.type == type }
        _records.add(HealthRecord(date, type, value))
    }

    fun getTodayValue(type: String): Float {
        return _records.find { it.date == LocalDate.now() && it.type == type }?.value ?: 0f
    }

    fun getChartData(type: String, period: String): List<Pair<String, Float>> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("M/d")

        return if (period == "단위(일)") {
            (4 downTo 0).map { i ->
                val date = today.minusDays(i.toLong())
                val value = _records.find { it.date == date && it.type == type }?.value ?: 0f
                Pair(date.format(formatter), value)
            }
        } else {
            (4 downTo 0).map { i ->
                val weekStart = today.minusWeeks(i.toLong()).with(DayOfWeek.MONDAY)
                var weeklySum = 0f
                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date <= today) {
                        weeklySum += _records.find { it.date == date && it.type == type }?.value ?: 0f
                    }
                }
                Pair("${weekStart.format(formatter)}~", weeklySum)
            }
        }
    }
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
    val healthState = remember { HealthState() }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainHealthSpectrumScreen(healthState = healthState, onNavigateToDetail = { itemName -> navController.navigate("detail/$itemName") })
        }
        composable("detail/{itemName}") { backStackEntry ->
            val itemName = backStackEntry.arguments?.getString("itemName") ?: "상세"
            DetailScreen(itemName = itemName, healthState = healthState, onBack = { navController.popBackStack() })
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
                    val stepsHistory = fetchHistoricalSteps(healthConnectClient, 35)
                    stepsHistory.forEach { (date, value) -> healthState.updateRecord(date, "걸음수", value) }

                    val sleepHistory = fetchHistoricalSleep(healthConnectClient, 35)
                    sleepHistory.forEach { (date, value) -> healthState.updateRecord(date, "수면", value) }
                }
                val screenTimeHistory = fetchHistoricalScreenTime(context, 35)
                screenTimeHistory.forEach { (date, value) -> healthState.updateRecord(date, "스크린 타임", value) }

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
                HealthApiRecord("🧍", "일어서기", "${healthState.getTodayValue("일어서기").toInt()}시간", progress = healthState.getTodayValue("일어서기") / healthState.standTarget.coerceAtLeast(1f), Color(0xFFFF9800), onClick = { onNavigateToDetail("일어서기") })
                HealthApiRecord("📱", "스크린 타임", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.getTodayValue("스크린 타임")), progress = healthState.getTodayValue("스크린 타임") / healthState.screenTimeTarget.coerceAtLeast(1f), Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// 세부 통계 화면 (직접 입력 및 팝업 추가)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    val unit = when (itemName) {
        "알코올", "카페인" -> "잔"
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

    val targetMax = when (itemName) {
        "걸음수" -> 20000f
        "수면", "일어서기", "스크린 타임" -> 24f
        "흡연" -> 40f
        "알코올", "카페인" -> 20f
        else -> 20f
    }

    val targetValue = when (itemName) {
        "알코올" -> healthState.alcoholTarget
        "흡연" -> healthState.smokingTarget
        "카페인" -> healthState.caffeineTarget
        "수면" -> healthState.sleepTarget
        "걸음수" -> healthState.stepsTarget
        "일어서기" -> healthState.standTarget
        "스크린 타임" -> healthState.screenTimeTarget
        else -> 10f
    }

    val onTargetChange: (Float) -> Unit = { newVal ->
        when (itemName) {
            "알코올" -> healthState.alcoholTarget = newVal
            "흡연" -> healthState.smokingTarget = newVal
            "카페인" -> healthState.caffeineTarget = newVal
            "수면" -> healthState.sleepTarget = newVal
            "걸음수" -> healthState.stepsTarget = newVal
            "일어서기" -> healthState.standTarget = newVal
            "스크린 타임" -> healthState.screenTimeTarget = newVal
        }
    }

    val currentValue = healthState.getTodayValue(itemName)
    val isManualInput = itemName in listOf("알코올", "흡연", "카페인")

    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }

    var showInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    val chartData = healthState.getChartData(itemName, selectedPeriod)

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(text = "$itemName 직접 입력", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = manualInputText,
                    onValueChange = { manualInputText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("수치 ($unit)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedValue = manualInputText.toFloatOrNull()
                    if (parsedValue != null) {
                        healthState.updateRecord(LocalDate.now(), itemName, parsedValue)
                    }
                    showInputDialog = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("취소", color = Color.Gray) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "$itemName 세부 통계", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (isManualInput) {
                Text(text = "오늘의 $itemName 기록 입력", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))

                val dynamicSliderMax = max(targetValue, currentValue).coerceAtLeast(1f)

                Slider(
                    value = currentValue.coerceIn(0f, dynamicSliderMax),
                    onValueChange = { newVal -> healthState.updateRecord(LocalDate.now(), itemName, newVal) },
                    valueRange = 0f..dynamicSliderMax,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${currentValue.toInt()} $unit",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                manualInputText = currentValue.toInt().toString()
                                showInputDialog = true
                            }
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    Text(text = "터치하여 숫자 직접 입력", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Text(text = "현재 기록된 수치 (자동 연동)", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val formattedVal = if (itemName == "걸음수") "${currentValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", currentValue)
                    Text(text = "$formattedVal $unit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    OutlinedButton(onClick = { expandedPeriod = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
                        Text(text = selectedPeriod, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                    }
                    DropdownMenu(expanded = expandedPeriod, onDismissRequest = { expandedPeriod = false }) {
                        DropdownMenuItem(text = { Text("단위(일)") }, onClick = { selectedPeriod = "단위(일)"; expandedPeriod = false })
                        DropdownMenuItem(text = { Text("단위(주)") }, onClick = { selectedPeriod = "단위(주)"; expandedPeriod = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            CustomBarChart(data = chartData, isWeekly = selectedPeriod == "단위(주)", modifier = Modifier.fillMaxWidth().height(250.dp))
            Spacer(modifier = Modifier.height(48.dp))

            Text(text = "목표 $itemName 설정 (메인 화면 최대치 연동)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = targetValue,
                onValueChange = onTargetChange,
                valueRange = 0f..targetMax,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD2B48C), activeTrackColor = Color(0xFFD2B48C)),
                modifier = Modifier.fillMaxWidth()
            )
            val formattedTarget = if (itemName == "걸음수") "${targetValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", targetValue)
            Text(text = "$formattedTarget $unit", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CustomBarChart(data: List<Pair<String, Float>>, isWeekly: Boolean, modifier: Modifier = Modifier) {
    val maxDataValue = (data.maxOfOrNull { it.second } ?: 10f).coerceAtLeast(10f)
    val yAxisLabels = listOf(maxDataValue.toInt().toString(), (maxDataValue / 2).toInt().toString(), "0")

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            yAxisLabels.forEach { label ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.width(40.dp), textAlign = TextAlign.End, maxLines = 1)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
                }
            }
        }
        Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            data.forEach { (_, value) ->
                val heightFraction = (value / maxDataValue).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                    Text(text = value.toInt().toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Box(modifier = Modifier.width(if (isWeekly) 40.dp else 24.dp).fillMaxHeight(heightFraction.coerceAtLeast(0.01f)).background(Color(0xFFD2B48C), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            data.forEach { (date, _) -> Text(text = date, fontSize = 10.sp, color = Color.Black, modifier = Modifier.width(if (isWeekly) 40.dp else 32.dp), textAlign = TextAlign.Center) }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp, start = 48.dp).fillMaxWidth().height(1.dp).background(Color.Black))
        Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp, bottom = 20.dp).fillMaxHeight().width(1.dp).background(Color.Black))
    }
}

// ==========================================
// 공용 UI 컴포넌트 모음
// ==========================================
@Composable
fun TopSpectrumBanner(onClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black).clickable { onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(60.dp).border(1.dp, Color.Black, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text(text = "😏", fontSize = 32.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "70", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(progress = { 0.7f }, modifier = Modifier.weight(1f).height(12.dp).padding(bottom = 6.dp), color = Color.Blue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "100", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(text = "수면은 충분하지만, 어제 음주를 많이 하셨네요.\n술을 줄이고 물을 많이 마셔볼까요?", fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(imageVector = Icons.Default.Info, contentDescription = "상세 정보", modifier = Modifier.align(Alignment.Top))
        }
    }
}

@Composable
fun HealthInputSlider(emoji: String, title: String, valueSuffix: String, value: Float, maxValue: Float, onValueChange: (Float) -> Unit, onClick: () -> Unit = {}) {
    val dynamicMax = max(maxValue, value).coerceAtLeast(1f)

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = "${value.toInt()}$valueSuffix", fontSize = 14.sp, color = if(value > maxValue) Color.Red else Color.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value.coerceIn(0f, dynamicMax),
                onValueChange = onValueChange,
                valueRange = 0f..dynamicMax,
                modifier = Modifier.height(24.dp)
            )
            Text(text = "Max: ${maxValue.toInt()}$valueSuffix", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun HealthApiRecord(emoji: String, title: String, value: String, progress: Float, color: Color, onClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = value, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).border(1.dp, Color.LightGray), color = color, trackColor = Color.Transparent)
        }
    }
}

// ==========================================
// 완벽한 "오늘" 기준 0% 오차 API 연동 함수들
// ==========================================
suspend fun fetchHistoricalSteps(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        // 걸음 수: 매일 00:00부터 23:59까지 하루치만 정확히 명시하여 Aggregate 쿼리
        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            // 당일이면 '현재 시간'까지, 과거일이면 '그 날짜의 끝'까지만 긁어옴
            val end = if (i == 0) Instant.now() else date.plusDays(1).atStartOfDay(zone).toInstant()

            val request = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = client.aggregate(request)
            val count = response[StepsRecord.COUNT_TOTAL] ?: 0L
            map[date] = count.toFloat()
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

suspend fun fetchHistoricalSleep(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            // 수면: 전날 낮 12시부터 당일 낮 12시까지를 '그 날의 수면'으로 엄격히 정의
            val start = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
            val end = date.atTime(12, 0).atZone(zone).toInstant()
            val actualEnd = if (end.isAfter(Instant.now())) Instant.now() else end

            val response = client.readRecords(ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, actualEnd)
            ))

            // 수면 중복 병합: 워치와 폰이 동시에 켜져 교집합으로 기록된 시간은 하나로 통합(Interval Merge)
            val sorted = response.records.sortedBy { it.startTime }
            var totalMinutes = 0L
            var currentEnd = Instant.MIN

            for (record in sorted) {
                if (record.endTime.isAfter(currentEnd)) {
                    val effectiveStart = if (record.startTime.isAfter(currentEnd)) record.startTime else currentEnd
                    totalMinutes += ChronoUnit.MINUTES.between(effectiveStart, record.endTime)
                    currentEnd = record.endTime
                }
            }
            map[date] = totalMinutes / 60f
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

fun fetchHistoricalScreenTime(context: Context, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startMilli = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMilli = if (i == 0) System.currentTimeMillis() else date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            // 스크린 타임: 앱들의 사용 시간을 더하는 것이 아니라 "화면이 켜져서 상호작용한 시간(SCREEN_INTERACTIVE)"만 추적
            val events = usageStatsManager.queryEvents(startMilli, endMilli)
            val event = android.app.usage.UsageEvents.Event()

            var totalScreenTime = 0L
            var lastInteractiveTime = 0L
            var isInteractive = false

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE) {
                    isInteractive = true
                    lastInteractiveTime = event.timeStamp
                } else if (event.eventType == android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    if (isInteractive && lastInteractiveTime > 0) {
                        totalScreenTime += (event.timeStamp - lastInteractiveTime)
                        isInteractive = false
                    }
                }
            }
            // 검색 종료 시점까지 아직 폰 화면이 켜져 있는 경우 마지막 시간 추가
            if (isInteractive && lastInteractiveTime > 0) {
                totalScreenTime += (endMilli - lastInteractiveTime)
            }

            // 만약 기기 제조사 문제로 이벤트 추적이 막혀 0이 나올 경우를 대비한 안전 장치(폴백)
            if (totalScreenTime == 0L) {
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val launchers = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
                val stats = usageStatsManager.queryAndAggregateUsageStats(startMilli, endMilli)
                for ((packageName, stat) in stats) {
                    if (stat.totalTimeInForeground > 0 && !launchers.contains(packageName)) {
                        totalScreenTime += stat.totalTimeInForeground
                    }
                }
                totalScreenTime = totalScreenTime.coerceAtMost(24L * 60 * 60 * 1000) // 최대 24시간 초과 방지
            }

            map[date] = totalScreenTime.toFloat() / (1000f * 60f * 60f)
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}