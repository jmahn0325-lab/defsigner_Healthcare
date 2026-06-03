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
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
                val value = when (type) {
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
            MainHealthSpectrumScreen(
                healthState = healthState,
                onNavigateToDetail = { itemName -> navController.navigate("detail/$itemName") }
            )
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

    // 초기 로딩 시 한 번 동기화 시도
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

                // 일어서기(활동 시간) UI 포맷 설정 (1시간 미만이면 분 단위 표기, 이상이면 소수점 시간 표기)
                val activeTime = healthState.getTodayValue("일어서기")
                val activeDisplay = if (activeTime < 1f && activeTime > 0f) {
                    "${(activeTime * 60).toInt()}분"
                } else {
                    String.format(java.util.Locale.getDefault(), "%.1f시간", activeTime)
                }
                HealthApiRecord("🧍", "일어서기", activeDisplay, progress = activeTime / healthState.standTarget.coerceAtLeast(1f), Color(0xFFFF9800), onClick = { onNavigateToDetail("일어서기") })

                HealthApiRecord("📱", "스크린 타임", String.format(java.util.Locale.getDefault(), "%.1f시간", healthState.getTodayValue("스크린 타임")), progress = healthState.getTodayValue("스크린 타임") / healthState.screenTimeTarget.coerceAtLeast(1f), Color(0xFF2196F3), onClick = { onNavigateToDetail("스크린 타임") })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// 세부 통계 화면
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }

    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("잔") }

    val isConvertible = itemName in listOf("알코올", "카페인")
    val displayUnit = if (isConvertible) selectedUnit else when (itemName) {
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

    // 알코올 1잔 = 8ml, 카페인 1잔 = 150ml 환산 비율 적용
    val multiplier = if (isConvertible && selectedUnit == "ml") {
        if (itemName == "알코올") 8f else 150f
    } else {
        1f
    }

    val targetMax = when (itemName) {
        "걸음수" -> 20000f
        "수면", "일어서기", "스크린 타임" -> 24f
        "흡연" -> 40f
        "알코올", "카페인" -> 20f
        else -> 20f
    }
    val displayTargetMax = targetMax * multiplier

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
    val displayTargetValue = targetValue * multiplier

    val onTargetChange: (Float) -> Unit = { newVal ->
        val internalVal = newVal / multiplier
        when (itemName) {
            "알코올" -> healthState.alcoholTarget = internalVal
            "흡연" -> healthState.smokingTarget = internalVal
            "카페인" -> healthState.caffeineTarget = internalVal
            "수면" -> healthState.sleepTarget = internalVal
            "걸음수" -> healthState.stepsTarget = internalVal
            "일어서기" -> healthState.standTarget = internalVal
            "스크린 타임" -> healthState.screenTimeTarget = internalVal
        }
    }

    val currentValue = healthState.getTodayValue(itemName)
    val displayCurrentValue = currentValue * multiplier
    val isManualInput = itemName in listOf("알코올", "흡연", "카페인")

    var showInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    val rawChartData = healthState.getChartData(itemName, selectedPeriod)
    val chartData = rawChartData.map { Pair(it.first, it.second * multiplier) }

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(text = "$itemName 직접 입력", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = manualInputText,
                    onValueChange = { manualInputText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("수치 ($displayUnit)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedValue = manualInputText.toFloatOrNull()
                    if (parsedValue != null) {
                        healthState.updateRecord(LocalDate.now(), itemName, parsedValue / multiplier)
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

                val dynamicSliderMax = max(displayTargetValue, displayCurrentValue).coerceAtLeast(1f * multiplier)

                Slider(
                    value = displayCurrentValue.coerceIn(0f, dynamicSliderMax),
                    onValueChange = { newVal -> healthState.updateRecord(LocalDate.now(), itemName, newVal / multiplier) },
                    valueRange = 0f..dynamicSliderMax,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedInputVal = if (displayCurrentValue == displayCurrentValue.toInt().toFloat()) "${displayCurrentValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayCurrentValue)
                    Text(
                        text = "$formattedInputVal $displayUnit",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                manualInputText = formattedInputVal
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
                    val formattedVal = if (itemName == "걸음수") "${displayCurrentValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayCurrentValue)
                    Text(text = "$formattedVal $displayUnit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (isConvertible) {
                    Box {
                        OutlinedButton(onClick = { expandedUnit = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
                            Text(text = "단위($selectedUnit)", color = Color.Black)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                        }
                        DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                            DropdownMenuItem(text = { Text("단위(잔)") }, onClick = { selectedUnit = "잔"; expandedUnit = false })
                            DropdownMenuItem(text = { Text("단위(ml)") }, onClick = { selectedUnit = "ml"; expandedUnit = false })
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

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
                value = displayTargetValue,
                onValueChange = onTargetChange,
                valueRange = 0f..displayTargetMax,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFD2B48C), activeTrackColor = Color(0xFFD2B48C)),
                modifier = Modifier.fillMaxWidth()
            )
            val formattedTarget = if (itemName == "걸음수") "${displayTargetValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayTargetValue)
            Text(text = "$formattedTarget $displayUnit", fontSize = 20.sp, fontWeight = FontWeight.Bold)

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
// 패키지별 분리로 중복을 제거한 걸음수 계산
// ==========================================
suspend fun fetchHistoricalSteps(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowInstant = Instant.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay(zoneId).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val actualEnd = if (endOfDay.isAfter(nowInstant)) nowInstant else endOfDay

            if (startOfDay.isAfter(actualEnd)) {
                map[date] = 0f
                continue
            }

            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, actualEnd)
            )

            val response = client.readRecords(request)

            val stepsByPackage = mutableMapOf<String, Long>()
            response.records.forEach { record ->
                val packageName = record.metadata.dataOrigin.packageName
                stepsByPackage[packageName] = (stepsByPackage[packageName] ?: 0L) + record.count
            }

            val maxSteps = stepsByPackage.values.maxOrNull() ?: 0L
            map[date] = maxSteps.toFloat()
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

// ==========================================
// ★ 버그 픽스: 24시간 통짜 데이터 걸러내기 및 캡핑(Capping) 적용
// ==========================================
suspend fun fetchHistoricalActiveTime(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowInstant = Instant.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay(zoneId).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val actualEnd = if (endOfDay.isAfter(nowInstant)) nowInstant else endOfDay

            if (startOfDay.isAfter(actualEnd)) {
                map[date] = 0f
                continue
            }

            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, actualEnd)
            )

            val response = client.readRecords(request)

            // 1. 주력 앱(가장 걸음수가 많은 앱) 선정
            val stepsByPackage = mutableMapOf<String, Long>()
            response.records.forEach { record ->
                val packageName = record.metadata.dataOrigin.packageName
                stepsByPackage[packageName] = (stepsByPackage[packageName] ?: 0L) + record.count
            }
            val targetPackage = stepsByPackage.maxByOrNull { it.value }?.key

            if (targetPackage == null) {
                map[date] = 0f
                continue
            }

            val targetRecords = response.records.filter { it.metadata.dataOrigin.packageName == targetPackage }

            // 2. 24시간 통짜 요약 데이터 제거
            // 단일 기록이 4시간(14400초) 이상인 것은 사용자의 상세 활동 시간이 아니라 앱이 임의로 뭉뚱그려놓은 통짜 데이터로 간주합니다.
            val detailedRecords = targetRecords.filter {
                Duration.between(it.startTime, it.endTime).seconds < 14400
            }

            var totalActiveSeconds = 0L

            if (detailedRecords.isEmpty() && targetRecords.isNotEmpty()) {
                // 통짜 요약 데이터만 있고 상세 데이터가 없는 경우 (만보기 앱 등)
                // 1보당 평균 보행 시간인 0.8초를 곱하여 현실적인 시간을 추정합니다.
                val totalSteps = targetRecords.sumOf { it.count }
                totalActiveSeconds = (totalSteps * 0.8).toLong()
            } else {
                // 3. 정상적인 상세 데이터들의 시간 순 정렬 및 Interval Merge (시간 캡핑 적용)
                val sortedRecords = detailedRecords.sortedBy { it.startTime }
                var currentEnd = Instant.MIN

                for (record in sortedRecords) {
                    val start = record.startTime
                    val end = record.endTime

                    if (end.isAfter(currentEnd)) {
                        val effectiveStart = if (start.isAfter(currentEnd)) start else currentEnd
                        val diffSeconds = Duration.between(effectiveStart, end).seconds

                        // 아무리 느려도 1보당 2초를 초과하여 걷기 힘들다는 현실적인 한계 적용 (데이터 뻥튀기 방어)
                        val maxPlausibleSeconds = record.count * 2L

                        val durationSeconds = if (diffSeconds == 0L && record.count > 0) {
                            10L // 동시 기록이라도 걸음이 있으면 최소 10초 부여
                        } else {
                            minOf(diffSeconds, maxPlausibleSeconds)
                        }

                        totalActiveSeconds += durationSeconds

                        // 보정된 실제 활동 시간만큼만 종료 시간을 밀어주어 다음 구간 검사에 활용
                        currentEnd = effectiveStart.plusSeconds(durationSeconds)
                    }
                }
            }

            // 초 단위를 시간 단위(0.0~24.0)로 변환
            map[date] = totalActiveSeconds.toFloat() / 3600f
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

suspend fun fetchHistoricalSleep(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowInstant = Instant.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val start = date.minusDays(1).atTime(12, 0).atZone(zoneId).toInstant()
            val end = date.atTime(12, 0).atZone(zoneId).toInstant()
            val actualEnd = if (end.isAfter(nowInstant)) nowInstant else end

            if (start.isAfter(actualEnd)) continue

            val response = client.readRecords(ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, actualEnd)
            ))

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
        val packageManager = context.packageManager
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val launchers = packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()

        val systemPackages = setOf(
            "com.android.systemui",
            "com.android.settings",
            "android",
            "com.samsung.android.app.aodservice",
            "com.samsung.android.app.cocktailbarservice"
        )

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startMilli = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMilli = if (i == 0) System.currentTimeMillis() else date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val stats = usageStatsManager.queryAndAggregateUsageStats(startMilli, endMilli)

            var totalTime = 0L
            for ((packageName, stat) in stats) {
                if (stat.totalTimeInForeground > 0 &&
                    !launchers.contains(packageName) &&
                    !systemPackages.contains(packageName)
                ) {
                    totalTime += stat.totalTimeInForeground
                }
            }
            map[date] = totalTime.toFloat() / (1000f * 60f * 60f)
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