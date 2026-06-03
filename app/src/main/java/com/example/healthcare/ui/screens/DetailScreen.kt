package com.example.healthcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcare.data.HealthState
import com.example.healthcare.ui.components.CustomBarChart
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }

    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("잔") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isConvertible = itemName in listOf("알코올", "카페인")
    val displayUnit = if (isConvertible) selectedUnit else when (itemName) {
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedValue = manualInputText.toFloatOrNull()
                    if (parsedValue != null) {
                        val newValue = max(parsedValue / multiplier, healthState.getTodayValue(itemName))
                        val nowHour = LocalTime.now().hour
                        healthState.updateManualRecord(LocalDate.now(), nowHour, itemName, newValue)
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(text = "$itemName 통계", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Current Status Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isManualInput) "오늘의 기록" else "현재 측정된 수치",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val formattedVal = if (itemName == "걸음수") "${displayCurrentValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayCurrentValue)
                    Text(
                        text = "$formattedVal $displayUnit",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    if (isManualInput) {
                        Spacer(modifier = Modifier.height(16.dp))
                        var sliderValue by remember(displayCurrentValue) { mutableStateOf(displayCurrentValue) }
                        val dynamicSliderMax = max(displayTargetValue, sliderValue).coerceAtLeast(1f * multiplier)

                        Slider(
                            value = sliderValue.coerceIn(displayCurrentValue, dynamicSliderMax),
                            onValueChange = { sliderValue = it },
                            valueRange = displayCurrentValue.coerceAtMost(dynamicSliderMax)..dynamicSliderMax,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    manualInputText = if (sliderValue == sliderValue.toInt().toFloat()) "${sliderValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", sliderValue)
                                    showInputDialog = true
                                }
                            ) {
                                Text("직접 입력하기", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = {
                                    val nowHour = LocalTime.now().hour
                                    healthState.updateManualRecord(LocalDate.now(), nowHour, itemName, sliderValue / multiplier)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("기록 저장", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chart Section
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "활동 통계", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row {
                    if (isConvertible) {
                        TextButton(onClick = { expandedUnit = true }) {
                            Text("단위($selectedUnit)")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                            DropdownMenuItem(text = { Text("단위(잔)") }, onClick = { selectedUnit = "잔"; expandedUnit = false })
                            DropdownMenuItem(text = { Text("단위(ml)") }, onClick = { selectedUnit = "ml"; expandedUnit = false })
                        }
                    }
                    TextButton(onClick = { expandedPeriod = true }) {
                        Text(selectedPeriod)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expandedPeriod, onDismissRequest = { expandedPeriod = false }) {
                        DropdownMenuItem(text = { Text("단위(일)") }, onClick = { selectedPeriod = "단위(일)"; expandedPeriod = false })
                        DropdownMenuItem(text = { Text("단위(주)") }, onClick = { selectedPeriod = "단위(주)"; expandedPeriod = false })
                    }
                }
            }

            CustomBarChart(data = chartData, isWeekly = selectedPeriod == "단위(주)", modifier = Modifier.fillMaxWidth().height(250.dp))
            
            Spacer(modifier = Modifier.height(32.dp))

            // Target Setting Section
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "목표 $itemName 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "메인 화면의 게이지와 연동됩니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Slider(
                        value = displayTargetValue,
                        onValueChange = onTargetChange,
                        valueRange = 0f..displayTargetMax,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                    
                    val formattedTarget = if (itemName == "걸음수") "${displayTargetValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayTargetValue)
                    Text(
                        text = "$formattedTarget $displayUnit",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
