package com.example.healthcare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, onBack: () -> Unit) {
    // 항목에 따른 단위 설정
    val unit = when (itemName) {
        "알코올", "카페인" -> "잔"
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

    // 상태 관리 (실제 앱에서는 ViewModel에서 관리해야 함)
    var currentValue by remember { mutableFloatStateOf(7f) }
    var targetValue by remember { mutableFloatStateOf(5f) }
    
    // 드롭다운 상태 관리
    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }
    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("단위($unit)") }

    // 임시 차트 데이터 (날짜별 수치)
    val chartData = listOf(
        Pair("3/25", 5f),
        Pair("3/26", 7f),
        Pair("3/27", 0f),
        Pair("3/28", 1f),
        Pair("3/29", 3f)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = itemName, fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
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

            // 1. 현재 섭취량/수치 입력 슬라이더
            Text(
                text = "슬라이더로 직접 입력",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Start)
            )
            Slider(
                value = currentValue,
                onValueChange = { currentValue = it },
                valueRange = 0f..20f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${currentValue.roundToInt()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 필터 드롭다운 (기간, 단위)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 기간 설정 드롭다운
                Box {
                    OutlinedButton(
                        onClick = { expandedPeriod = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = selectedPeriod, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                    }
                    DropdownMenu(
                        expanded = expandedPeriod,
                        onDismissRequest = { expandedPeriod = false }
                    ) {
                        DropdownMenuItem(text = { Text("단위(일)") }, onClick = { selectedPeriod = "단위(일)"; expandedPeriod = false })
                        DropdownMenuItem(text = { Text("단위(주)") }, onClick = { selectedPeriod = "단위(주)"; expandedPeriod = false })
                        DropdownMenuItem(text = { Text("단위(월)") }, onClick = { selectedPeriod = "단위(월)"; expandedPeriod = false })
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                // 단위 설정 드롭다운
                Box {
                    OutlinedButton(
                        onClick = { expandedUnit = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = selectedUnit, color = Color.Black)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                    }
                    DropdownMenu(
                        expanded = expandedUnit,
                        onDismissRequest = { expandedUnit = false }
                    ) {
                        DropdownMenuItem(text = { Text("단위($unit)") }, onClick = { selectedUnit = "단위($unit)"; expandedUnit = false })
                        if (itemName == "알코올" || itemName == "카페인") {
                            DropdownMenuItem(text = { Text("단위(ml)") }, onClick = { selectedUnit = "단위(ml)"; expandedUnit = false })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. 기간별 세부 통계 (막대 그래프)
            CustomBarChart(
                data = chartData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 4. 목표 설정 슬라이더
            Text(
                text = "목표 $itemName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = targetValue,
                onValueChange = { targetValue = it },
                valueRange = 0f..20f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD2B48C), // 디자인과 유사한 베이지/브라운 톤
                    activeTrackColor = Color(0xFFD2B48C)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${targetValue.roundToInt()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// 순수 Compose로 구현한 커스텀 막대 그래프 컴포저블
@Composable
fun CustomBarChart(data: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    val maxDataValue = (data.maxOfOrNull { it.second } ?: 10f).coerceAtLeast(10f) // Y축 최대값 (최소 10)
    val yAxisLabels = listOf(maxDataValue.toInt().toString(), (maxDataValue / 2).toInt().toString(), "0")

    Box(modifier = modifier) {
        // Y축 및 배경 가이드라인
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            yAxisLabels.forEach { label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Divider(color = Color.LightGray, thickness = 1.dp)
                }
            }
        }

        // X축 데이터 및 막대
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 8.dp, bottom = 8.dp), // Y축 라벨 공간 확보
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (date, value) ->
                val heightFraction = value / maxDataValue
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // 막대 위의 수치
                    Text(
                        text = value.toInt().toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    // 막대 그래프
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .fillMaxHeight(heightFraction.coerceAtLeast(0.01f)) // 최소 높이 보장
                            .background(Color(0xFFD2B48C), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp)) // X축 라벨과의 간격
                }
            }
        }

        // X축 라벨 (막대와 정렬을 맞추기 위해 별도의 Row 사용)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (date, _) ->
                Text(
                    text = date,
                    fontSize = 12.sp,
                    color = Color.Black,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // X축 진한 선
        Divider(
            color = Color.Black, 
            thickness = 1.dp, 
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp, start = 32.dp)
        )
        // Y축 진한 선
        Divider(
            color = Color.Black, 
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(1.dp).padding(start = 32.dp, bottom = 18.dp)
        )
    }
}