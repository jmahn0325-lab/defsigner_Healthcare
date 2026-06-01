package com.example.healthcare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    // 항목별 단위 설정
    val unit = when (itemName) {
        "알코올", "카페인" -> "잔"
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

    // 항목별 목표 설정 슬라이더의 절대 최대치
    val targetMax = when (itemName) {
        "걸음수" -> 20000f
        "수면", "일어서기", "스크린 타임" -> 24f
        "흡연" -> 40f
        "알코올", "카페인" -> 20f
        else -> 20f
    }

    // 스마트 라우팅: HealthState와 연동
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

    val currentValue = when (itemName) {
        "알코올" -> healthState.alcoholCurrent
        "흡연" -> healthState.smokingCurrent
        "카페인" -> healthState.caffeineCurrent
        "수면" -> healthState.sleepCurrent
        "걸음수" -> healthState.stepsCurrent
        "일어서기" -> healthState.standCurrent
        "스크린 타임" -> healthState.screenTimeCurrent
        else -> 0f
    }

    // 직접 입력 항목(알코올, 흡연, 카페인)인지 확인
    val isManualInput = itemName in listOf("알코올", "흡연", "카페인")

    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }

    // 임시 차트 데이터 (추후 ViewModel/DB 연동 시 교체)
    val chartData = listOf(
        Pair("3/25", (targetValue * 0.8f)),
        Pair("3/26", (targetValue * 1.2f)),
        Pair("3/27", (targetValue * 0.3f)),
        Pair("3/28", (targetValue * 0.5f)),
        Pair("3/29", currentValue)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "$itemName 세부 통계", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기") }
                }
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

            // ==========================================
            // 1. 현재 수치 영역 (수동 vs 자동 UI 다름)
            // ==========================================
            if (isManualInput) {
                Text(text = "오늘의 $itemName 기록 입력", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Slider(
                    value = currentValue.coerceIn(0f, targetValue.coerceAtLeast(1f)),
                    onValueChange = { newVal ->
                        when (itemName) {
                            "알코올" -> healthState.alcoholCurrent = newVal
                            "흡연" -> healthState.smokingCurrent = newVal
                            "카페인" -> healthState.caffeineCurrent = newVal
                        }
                    },
                    valueRange = 0f..targetValue.coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "${currentValue.toInt()} $unit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(text = "현재 기록된 수치 (자동 연동)", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val formattedVal = if (itemName == "걸음수") "${currentValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", currentValue)
                    Text(text = "$formattedVal $unit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 2. 필터 및 차트 영역
            // ==========================================
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
            CustomBarChart(data = chartData, modifier = Modifier.fillMaxWidth().height(250.dp))
            Spacer(modifier = Modifier.height(48.dp))

            // ==========================================
            // 3. 목표 설정 영역 (이 값이 메인 화면의 최대치가 됨)
            // ==========================================
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
fun CustomBarChart(data: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    val maxDataValue = (data.maxOfOrNull { it.second } ?: 10f).coerceAtLeast(10f)
    val yAxisLabels = listOf(maxDataValue.toInt().toString(), (maxDataValue / 2).toInt().toString(), "0")

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            yAxisLabels.forEach { label ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(8.dp))
                    HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxSize().padding(start = 44.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            data.forEach { (_, value) ->
                val heightFraction = (value / maxDataValue).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                    Text(text = value.toInt().toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Box(modifier = Modifier.width(32.dp).fillMaxHeight(heightFraction.coerceAtLeast(0.01f)).background(Color(0xFFD2B48C), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            data.forEach { (date, _) -> Text(text = date, fontSize = 12.sp, color = Color.Black, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center) }
        }
        HorizontalDivider(color = Color.Black, thickness = 1.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp, start = 44.dp))
        VerticalDivider(color = Color.Black, modifier = Modifier.align(Alignment.CenterStart).padding(start = 44.dp, bottom = 18.dp))
    }
}