package com.example.healthcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcare.data.HealthState
import com.example.healthcare.data.BeverageType
import com.example.healthcare.ui.components.CustomBarChart
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("단위(일)") }
    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("잔") }

    var showTypeDialog by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var newTypeName by remember { mutableStateOf("") }
    var newTypeContent by remember { mutableStateOf("") }

    val isConvertible = itemName in listOf("알코올", "카페인")
    val displayUnit = if (isConvertible) selectedUnit else when (itemName) {
        "흡연" -> "개비"
        "수면", "일어서기", "스크린 타임" -> "시간"
        "걸음수" -> "보"
        else -> "단위"
    }

    val multiplier = if (isConvertible && selectedUnit == "ml") {
        if (itemName == "알코올") 8f else 150f
    } else 1f

    val targetMax = when (itemName) {
        "걸음수" -> 20000f
        "수면", "일어서기", "스크린 타임" -> 24f
        "흡연" -> 26f
        "알코올" -> 24f
        "카페인" -> 30f
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

    var tempDisplayValue by remember(displayCurrentValue) { mutableFloatStateOf(displayCurrentValue) }
    val hasSliderChanges = tempDisplayValue > displayCurrentValue

    var showInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    val rawChartData = healthState.getChartData(itemName, selectedPeriod)
    val chartData = rawChartData.map { Pair(it.first, it.second * multiplier) }

    if (showTypeDialog) {
        val types = if (itemName == "알코올") healthState.alcoholTypes else healthState.caffeineTypes
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(text = "$itemName 종류 관리", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    types.forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "${type.name} (함유량: ${type.content})")
                            IconButton(onClick = { types.remove(type) }) {
                                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showAddTypeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("새 종류 추가")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTypeDialog = false }) { Text("닫기") } }
        )
    }

    if (showAddTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddTypeDialog = false },
            title = { Text(text = "새로운 $itemName 추가", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = newTypeName, onValueChange = { newTypeName = it }, label = { Text("제품명") })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTypeContent,
                        onValueChange = { newTypeContent = it },
                        label = { Text("함유량 (잔/개 기준)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val content = newTypeContent.toFloatOrNull() ?: 1f
                    val types = if (itemName == "알코올") healthState.alcoholTypes else healthState.caffeineTypes
                    types.add(BeverageType(newTypeName, content))
                    newTypeName = ""
                    newTypeContent = ""
                    showAddTypeDialog = false
                }) { Text("추가") }
            },
            dismissButton = { TextButton(onClick = { showAddTypeDialog = false }) { Text("취소") } }
        )
    }

    if (showInputDialog) {
        val isMlUnit = isConvertible && selectedUnit == "ml"
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(text = if (isMlUnit) "$itemName 섭취량 추가" else "$itemName 직접 입력", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (isMlUnit) {
                        Text(text = "현재 수치: ${displayCurrentValue.toInt()} $displayUnit", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(if (isMlUnit) "추가할 수량 ($displayUnit)" else "수치 ($displayUnit)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedValue = manualInputText.toFloatOrNull()
                    if (parsedValue != null) {
                        val finalValue = if (isMlUnit) {
                            displayCurrentValue + parsedValue
                        } else {
                            if (isManualInput) max(parsedValue.roundToInt().toFloat(), displayCurrentValue)
                            else max(parsedValue, displayCurrentValue)
                        }

                        if (isManualInput) {
                            healthState.updateManualRecord(LocalDate.now(), LocalTime.now().hour, itemName, finalValue / multiplier)
                        } else {
                            healthState.updateAutoRecord(LocalDate.now(), itemName, finalValue / multiplier)
                        }
                    }
                    showInputDialog = false
                }) { Text("확인") }
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (isManualInput) {
                val isSmoking = itemName == "흡연"
                val isGlassUnit = isConvertible && selectedUnit == "잔"
                val isMlUnit = isConvertible && selectedUnit == "ml"
                val showManualInput = !isSmoking && !isGlassUnit

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "오늘의 $itemName 기록 입력", fontSize = 14.sp, color = Color.Gray)
                    if (isConvertible) {
                        TextButton(onClick = { showTypeDialog = true }) {
                            Text(text = "(종류)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                val dynamicSliderMax = max(displayTargetValue, tempDisplayValue).coerceAtLeast(1f * multiplier)
                val sliderWeight = (dynamicSliderMax - displayCurrentValue).coerceAtLeast(0.01f)
                val baseWeight = displayCurrentValue.coerceAtLeast(0.01f)

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (baseWeight > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(baseWeight)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                        )
                    }
                    Slider(
                        value = tempDisplayValue.coerceIn(displayCurrentValue, dynamicSliderMax),
                        onValueChange = { newVal ->
                            tempDisplayValue = if (isMlUnit) newVal else {
                                val addedUnits = ((newVal - displayCurrentValue) / multiplier).roundToInt()
                                displayCurrentValue + addedUnits * multiplier
                            }
                        },
                        valueRange = displayCurrentValue..dynamicSliderMax,
                        steps = if (isMlUnit) 0 else ((dynamicSliderMax - displayCurrentValue) / multiplier).toInt().let { if (it > 0) it - 1 else 0 },
                        modifier = Modifier.weight(sliderWeight)
                    )
                }

                if (hasSliderChanges) {
                    Button(
                        onClick = { healthState.updateManualRecord(LocalDate.now(), LocalTime.now().hour, itemName, tempDisplayValue / multiplier) },
                        modifier = Modifier.align(Alignment.End).padding(bottom = 8.dp)
                    ) {
                        Text("저장")
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val displayBase = displayCurrentValue
                    val delta = tempDisplayValue - displayCurrentValue

                    val contentModifier = if (showManualInput) {
                        Modifier
                            .clickable {
                                manualInputText = if (isMlUnit) "" else "${tempDisplayValue.toInt()}"
                                showInputDialog = true
                            }
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = contentModifier
                    ) {
                        val baseText = if (displayBase == displayBase.toInt().toFloat()) "${displayBase.toInt()}" else String.format("%.1f", displayBase)
                        Text(text = baseText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                        if (hasSliderChanges) {
                            val deltaInUnits = delta / multiplier
                            val deltaText = if (isMlUnit) String.format(" +%.1f", delta) else " +${deltaInUnits.roundToInt()}"
                            Text(text = deltaText, fontSize = 16.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                        Text(text = " $displayUnit", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    if (showManualInput) {
                        Text(text = "섭취량 직접 입력", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
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

            if (!isManualInput) {
                Text(text = "목표 $itemName 설정 (메인 화면 최대치 연동)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Slider(value = displayTargetValue, onValueChange = onTargetChange, valueRange = 0f..displayTargetMax, colors = SliderDefaults.colors(thumbColor = Color(0xFFD2B48C), activeTrackColor = Color(0xFFD2B48C)), modifier = Modifier.fillMaxWidth())
                val formattedTarget = if (itemName == "걸음수") "${displayTargetValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", displayTargetValue)
                Text(text = "$formattedTarget $displayUnit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}