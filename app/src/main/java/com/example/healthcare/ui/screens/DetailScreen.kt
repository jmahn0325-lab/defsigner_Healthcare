package com.example.healthcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.healthcare.data.HealthState
import com.example.healthcare.data.BeverageType
import com.example.healthcare.ui.components.HealthBarChart
import com.example.healthcare.widget.HealthWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemName: String, healthState: HealthState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var expandedPeriod by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("기간(일)") }
    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("잔") }

    var showTypeDialog by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<BeverageType?>(null) }
    var newTypeName by remember { mutableStateOf("") }
    var newTypeContent by remember { mutableStateOf("") }
    var newTypeUnit by remember { mutableStateOf("") }

    val selectedType = if (itemName == "알코올") healthState.selectedAlcoholType else healthState.selectedCaffeineType
    val isConvertible = itemName in listOf("알코올", "카페인")
    
    val convertibleUnit = if (itemName == "알코올") "g" else "mg"
    val alternativeUnit = if (isConvertible) selectedType.unit else ""
    
    val displayUnit = if (isConvertible) {
        if (selectedUnit == "잔") alternativeUnit else selectedUnit
    } else when (itemName) {
        "흡연" -> "개비"
        "수면", "활동시간", "스크린 타임" -> "시간"
        else -> "단위"
    }

    val multiplier = if (isConvertible && selectedUnit == convertibleUnit) {
        selectedType.content
    } else 1f

    val targetMax = when (itemName) {
        "수면", "스크린 타임" -> 24f
        "활동시간" -> 5f // 활동시간 최대 목표치를 5시간으로 조정 (현실적 범위)
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
        "활동시간" -> healthState.activityTarget
        "스크린 타임" -> healthState.screenTimeTarget
        else -> 0f
    }
    // 슬라이더 논리 계산을 위해 multiplier가 적용된 타겟값
    val effectiveTargetValue = targetValue * multiplier
    // 차트 표시 여부를 결정하는 타겟값 (알코올, 흡연, 카페인은 null로 처리하여 라인 숨김)
    val displayTargetValue: Float? = if (itemName in listOf("알코올", "흡연", "카페인")) null else effectiveTargetValue

    val onTargetChange: (Float) -> Unit = { newVal ->
        val internalVal = newVal / multiplier
        when (itemName) {
            "알코올" -> healthState.alcoholTarget = internalVal
            "흡연" -> healthState.smokingTarget = internalVal
            "카페인" -> healthState.caffeineTarget = internalVal
            "수면" -> healthState.sleepTarget = internalVal
            "활동시간" -> healthState.activityTarget = internalVal
            "스크린 타임" -> healthState.screenTimeTarget = internalVal
        }
    }

    var targetInputText by remember(targetValue) { mutableStateOf(if (targetValue > 0) String.format(java.util.Locale.getDefault(), "%.1f", targetValue) else "") }

    val rawAbsoluteValue = healthState.getTodayValue(itemName)
    val displayCurrentValue = if (isConvertible && selectedUnit == "잔") {
        rawAbsoluteValue / selectedType.content
    } else {
        rawAbsoluteValue
    }
    val isManualInput = itemName in listOf("알코올", "흡연", "카페인", "수면")
    val hasPenaltyDetails = itemName in listOf("알코올", "흡연", "카페인", "수면", "스크린 타임", "활동시간")

    var tempDisplayValue by remember(displayCurrentValue, selectedUnit) { mutableFloatStateOf(displayCurrentValue) }
    val hasSliderChanges = tempDisplayValue > displayCurrentValue + 0.001f

    var showInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    
    var showPenaltyInfo by remember { mutableStateOf(false) }

    val rawChartData = healthState.getChartData(itemName, selectedPeriod.replace("기간", "단위"))
    val chartData = rawChartData.map { (date: String, value: Float) -> 
        val displayVal = if (isConvertible) {
            if (selectedUnit == "잔") value / selectedType.content else value
        } else {
            value
        }
        Pair(date, displayVal)
    }

    val updateWidgets = {
        scope.launch {
            HealthWidget.updateAllWidgets(context)
        }
    }

    if (showTypeDialog) {
        val types = if (itemName == "알코올") healthState.alcoholTypes else healthState.caffeineTypes
        val contentUnit = if (itemName == "알코올") "g" else "mg"
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(text = "$itemName 종류 관리", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    types.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (itemName == "알코올") {
                                        healthState.selectedAlcoholType = type
                                        healthState.saveSelection("알코올", type.name)
                                    } else {
                                        healthState.selectedCaffeineType = type
                                        healthState.saveSelection("카페인", type.name)
                                    }
                                    showTypeDialog = false
                                    updateWidgets()
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = type.name,
                                    fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal,
                                    color = if (type == selectedType) MaterialTheme.colorScheme.primary else Color.Black
                                )
                                Text(text = "1 ${type.unit}당 $itemName ${type.content}$contentUnit", fontSize = 12.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(onClick = { 
                                    editingType = type
                                    newTypeName = type.name
                                    newTypeContent = type.content.toString()
                                    newTypeUnit = type.unit
                                    showAddTypeDialog = true 
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "수정", tint = Color.Gray)
                                }
                                IconButton(onClick = { 
                                    if (types.size > 1) {
                                        if (type == selectedType) {
                                            val nextType = types.find { it != type }!!
                                            if (itemName == "알코올") healthState.selectedAlcoholType = nextType
                                            else healthState.selectedCaffeineType = nextType
                                        }
                                        types.remove(type)
                                        updateWidgets()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showAddTypeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("새 종류 추가")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTypeDialog = false }) { Text("닫기") } }
        )
    }

    if (showAddTypeDialog) {
        val contentUnit = if (itemName == "알코올") "g" else "mg"
        val isEditing = editingType != null
        AlertDialog(
            onDismissRequest = { 
                showAddTypeDialog = false 
                editingType = null
            },
            title = { Text(text = if (isEditing) "$itemName 수정" else "새로운 $itemName 추가", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = newTypeName, onValueChange = { newTypeName = it }, label = { Text("제품명 (예: 아메리카노)") })
                    Spacer(modifier = Modifier.height(8.dp))
                    val fieldLabel = if (itemName == "알코올") "섭취당 알코올 함량" else "섭취당 카페인 함량"
                    OutlinedTextField(
                        value = newTypeContent,
                        onValueChange = { newTypeContent = it },
                        label = { Text("$fieldLabel ($contentUnit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTypeUnit,
                        onValueChange = { newTypeUnit = it },
                        label = { Text("단위 (예: 잔, 캔, 병)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val content = newTypeContent.toFloatOrNull() ?: 1f
                    val unit = newTypeUnit.ifBlank { "단위" }
                    val types = if (itemName == "알코올") healthState.alcoholTypes else healthState.caffeineTypes
                    val newType = BeverageType(newTypeName, content, unit)
                    
                    if (isEditing) {
                        val index = types.indexOf(editingType)
                        if (index != -1) {
                            types[index] = newType
                            if (editingType == selectedType) {
                                if (itemName == "알코올") {
                                    healthState.selectedAlcoholType = newType
                                    healthState.saveSelection("알코올", newType.name)
                                } else {
                                    healthState.selectedCaffeineType = newType
                                    healthState.saveSelection("카페인", newType.name)
                                }
                            }
                        }
                    } else {
                        types.add(newType)
                        if (itemName == "알코올") {
                            healthState.selectedAlcoholType = newType
                            healthState.saveSelection("알코올", newType.name)
                        } else {
                            healthState.selectedCaffeineType = newType
                            healthState.saveSelection("카페인", newType.name)
                        }
                    }
                    
                    newTypeName = ""
                    newTypeContent = ""
                    newTypeUnit = ""
                    editingType = null
                    showAddTypeDialog = false
                    showTypeDialog = false
                    updateWidgets()
                }) { Text(if (isEditing) "수정 완료" else "추가 및 선택") }
            },
            dismissButton = { 
                TextButton(onClick = { 
                    showAddTypeDialog = false 
                    editingType = null
                    newTypeUnit = ""
                }) { Text("취소") } 
            }
        )
    }

    if (showInputDialog) {
        val isContinuousUnit = isConvertible && (selectedUnit == "g" || selectedUnit == "mg")
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(text = if (isContinuousUnit) "$itemName 섭취량 추가" else "$itemName 직접 입력", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (isContinuousUnit) {
                        Text(text = "현재 수치: ${displayCurrentValue.toInt()} $displayUnit", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(if (isContinuousUnit) "추가할 수량 ($displayUnit)" else "수치 ($displayUnit)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedValue = manualInputText.toFloatOrNull()
                    if (parsedValue != null) {
                        val finalDisplayValue = if (isContinuousUnit) {
                            displayCurrentValue + parsedValue
                        } else {
                            if (itemName == "수면") parsedValue // 수면은 입력한 값 그대로 적용 (덮어쓰기)
                            else if (isManualInput) max(parsedValue.roundToInt().toFloat(), displayCurrentValue)
                            else max(parsedValue, displayCurrentValue)
                        }

                        val displayToInternalMultiplier = if (isConvertible && selectedUnit == "잔") selectedType.content else 1f
                        
                        if (itemName == "수면") {
                            // 수면은 자동 기록과 동일하게 전체 값을 덮어씌움
                            healthState.updateAutoRecord(LocalDate.now(), itemName, finalDisplayValue)
                        } else if (isManualInput) {
                            val now = LocalTime.now()
                            healthState.updateManualRecord(
                                LocalDate.now(), 
                                now.hour,
                                now.minute,
                                now.second,
                                itemName, 
                                finalDisplayValue * displayToInternalMultiplier,
                                if (isConvertible) selectedType.name else if (itemName == "흡연") "담배" else null,
                                if (isConvertible) selectedType.unit else if (itemName == "흡연") "개비" else null
                            )
                        } else {
                            healthState.updateAutoRecord(LocalDate.now(), itemName, finalDisplayValue * displayToInternalMultiplier)
                        }
                        updateWidgets()
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 3. 감점 총합 요약 표시 (최상단)
            if (hasPenaltyDetails) {
                val totalPenalty = healthState.getTotalCurrentPenalty(itemName)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (totalPenalty < 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (totalPenalty < 0) Color(0xFFEF9A9A) else Color(0xFFA5D6A7))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = if (itemName == "수면") "현재 수면 건강 점수 영향" else "현재 적용 중인 총 감점", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "%.2f점", totalPenalty),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalPenalty < 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                        )
                    }
                }
            }

            if (isManualInput) {
                val isSmoking = itemName == "흡연"
                val isGlassUnit = isConvertible && selectedUnit == "잔"
                val isContinuousUnit = isConvertible && (selectedUnit == "g" || selectedUnit == "mg")
                val showManualInput = !isSmoking && !isGlassUnit

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "오늘의 $itemName 기록 입력", fontSize = 14.sp, color = Color.Gray)
                    if (isConvertible) {
                        Surface(
                            onClick = { showTypeDialog = true },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "종류: ${selectedType.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                val dynamicSliderMax = max(effectiveTargetValue, tempDisplayValue).coerceAtLeast(1f * multiplier)
                val sliderWeight = (dynamicSliderMax - displayCurrentValue).coerceAtLeast(0.01f)
                val baseWeight = displayCurrentValue.coerceAtLeast(0.01f)

                if (itemName != "수면") {
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
                                tempDisplayValue = if (isContinuousUnit) newVal else {
                                    val addedUnits = (newVal - displayCurrentValue).roundToInt()
                                    displayCurrentValue + addedUnits
                                }
                            },
                            valueRange = displayCurrentValue..dynamicSliderMax,
                            steps = if (isContinuousUnit) 0 else (dynamicSliderMax - displayCurrentValue).toInt().let { if (it > 0) it - 1 else 0 },
                            modifier = Modifier.weight(sliderWeight)
                        )
                    }
                }

                if (hasSliderChanges) {
                    Button(
                        onClick = { 
                            val displayToInternalMultiplier = if (isConvertible && selectedUnit == "잔") selectedType.content else 1f
                            val now = LocalTime.now()
                            healthState.updateManualRecord(
                                LocalDate.now(), 
                                now.hour,
                                now.minute,
                                now.second,
                                itemName, 
                                tempDisplayValue * displayToInternalMultiplier,
                                if (isConvertible) selectedType.name else if (itemName == "흡연") "담배" else null,
                                if (isConvertible) selectedType.unit else if (itemName == "흡연") "개비" else null
                            ) 
                            updateWidgets()
                        },
                        modifier = Modifier.align(Alignment.End).padding(bottom = 8.dp)
                    ) {
                        Text("저장")
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val delta = tempDisplayValue - displayCurrentValue

                    val contentModifier = if (showManualInput) {
                        Modifier
                            .clickable {
                                manualInputText = if (isContinuousUnit) "" else "${tempDisplayValue.toInt()}"
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
                        val formattedCurrent = if (itemName == "수면") {
                            val totalMinutes = (displayCurrentValue * 60).roundToInt()
                            val h = totalMinutes / 60
                            val m = totalMinutes % 60
                            when {
                                h > 0 && m > 0 -> "${h}시간 ${m}분"
                                h > 0 -> "${h}시간"
                                else -> "${m}분"
                            }
                        } else if (displayCurrentValue == displayCurrentValue.toInt().toFloat()) {
                            "${displayCurrentValue.toInt()}"
                        } else {
                            String.format(java.util.Locale.getDefault(), "%.1f", displayCurrentValue)
                        }

                        Text(text = formattedCurrent, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                        if (hasSliderChanges) {
                            val deltaText = if (itemName == "수면") {
                                val totalMinutes = (delta * 60).roundToInt()
                                val h = totalMinutes / 60
                                val m = totalMinutes % 60
                                when {
                                    h > 0 && m > 0 -> " +${h}시간 ${m}분"
                                    h > 0 -> " +${h}시간"
                                    else -> " +${m}분"
                                }
                            } else if (isContinuousUnit) {
                                String.format(java.util.Locale.getDefault(), " +%.1f", delta)
                            } else {
                                " +${delta.roundToInt()}"
                            }
                            Text(text = deltaText, fontSize = 16.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                        Text(text = if (itemName == "수면") "" else " $displayUnit", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    if (showManualInput || itemName == "수면") {
                        Text(text = if (itemName == "수면") "수면 시간 직접 입력" else "섭취량 직접 입력", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                Text(text = "현재 기록된 수치 (자동 연동)", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    val formattedVal = if (itemName == "활동시간" || itemName == "스크린 타임") {
                        val totalMinutes = (displayCurrentValue * 60).roundToInt()
                        val h = totalMinutes / 60
                        val m = totalMinutes % 60
                        when {
                            h > 0 && m > 0 -> "${h}시간 ${m}분"
                            h > 0 -> "${h}시간"
                            else -> "${m}분"
                        }
                    } else {
                        String.format(java.util.Locale.getDefault(), "%.1f", displayCurrentValue)
                    }
                    Text(text = "$formattedVal ${if (itemName == "활동시간" || itemName == "스크린 타임") "" else displayUnit}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (isConvertible) {
                    val unitLabel = if (itemName == "알코올") "g" else "mg"
                    Box {
                        OutlinedButton(onClick = { expandedUnit = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
                            Text(text = "단위($displayUnit)", color = Color.Black)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                        }
                        DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                            DropdownMenuItem(text = { Text("단위($alternativeUnit)") }, onClick = { selectedUnit = "잔"; expandedUnit = false })
                            DropdownMenuItem(text = { Text("단위($unitLabel)") }, onClick = { selectedUnit = unitLabel; expandedUnit = false })
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
                        DropdownMenuItem(text = { Text("기간(일)") }, onClick = { selectedPeriod = "기간(일)"; expandedPeriod = false })
                        DropdownMenuItem(text = { Text("기간(주)") }, onClick = { selectedPeriod = "기간(주)"; expandedPeriod = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            val isHigherBetter = itemName in listOf("활동시간", "수면")
            HealthBarChart(
                data = chartData,
                isWeekly = selectedPeriod == "기간(주)",
                modifier = Modifier.fillMaxWidth().height(250.dp),
                yAxisTitle = displayUnit,
                targetValue = displayTargetValue,
                isHigherBetter = isHigherBetter
            )
            
            // 시점별 감점 상세 내역 추가 (흡연, 알코올, 카페인, 수면 해당)
            if (hasPenaltyDetails) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = if (itemName == "수면") "일자별 수면 점수 내역 (최근 7일)" else "시점별 감점 상세 내역 (최근 7일)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                
                val penaltyDetails = healthState.getPenaltyDetails(itemName).reversed()
                
                // key(healthState.activityTarget)를 사용하여 Recomposition 유도
                key(if (itemName == "활동시간") healthState.activityTarget else 0f) {
                    if (penaltyDetails.isEmpty()) {
                        Text(text = "기록된 데이터가 없습니다.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(scrollState)
                                    .padding(12.dp)
                            ) {
                                penaltyDetails.forEach { detail ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            // 1. 시점 정확히 반영 (MM/dd H:mm 형식, 수면은 날짜만)
                                            val timePattern = if (itemName == "수면") "MM/dd" else if (detail.minute != null) "MM/dd HH:mm" else "MM/dd HH:00"
                                            Text(
                                                text = detail.dateTime.withMinute(detail.minute ?: 0).format(DateTimeFormatter.ofPattern(timePattern)),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val intakeText = if (detail.itemName != null && detail.unit != null) {
                                                val contentUnit = if (itemName == "알코올") "g" else "mg"
                                                val count = if (itemName == "알코올") {
                                                    detail.originalValue / (healthState.alcoholTypes.find { it.name == detail.itemName }?.content ?: 1f)
                                                } else if (itemName == "카페인") {
                                                    detail.originalValue / (healthState.caffeineTypes.find { it.name == detail.itemName }?.content ?: 1f)
                                                } else {
                                                    detail.originalValue
                                                }
                                                val countStr = if (count == count.toInt().toFloat()) "${count.toInt()}" else String.format("%.1f", count)
                                                "${detail.itemName} ${countStr}${detail.unit} / ${detail.originalValue.toInt()}$contentUnit"
                                            } else {
                                                val unitInDetail = if (isConvertible) convertibleUnit else displayUnit
                                                val formattedValue = if (itemName == "수면" || itemName == "스크린 타임" || itemName == "활동시간") {
                                                    val totalMinutes = (detail.originalValue * 60).roundToInt()
                                                    val h = totalMinutes / 60
                                                    val m = totalMinutes % 60
                                                    when {
                                                        h > 0 && m > 0 -> "${h}시간 ${m}분"
                                                        h > 0 -> "${h}시간"
                                                        else -> "${m}분"
                                                    }
                                                } else "${detail.originalValue.toInt()}"
                                                "$formattedValue$unitInDetail"
                                            }
                                            
                                            val penaltyLabel = if (detail.isOverThreshold) {
                                                when (itemName) {
                                                    "수면" -> " (수면 부족 💤)"
                                                    "활동시간" -> " (활동 부족 🏃)"
                                                    "스크린 타임" -> ""
                                                    else -> " (폭주 페널티 🔥)"
                                                }
                                            } else ""
                                            
                                            Text(
                                                text = "기록: $intakeText$penaltyLabel",
                                                fontSize = 12.sp,
                                                color = if (detail.isOverThreshold && itemName != "수면") Color(0xFFD32F2F) else Color.Gray
                                            )
                                        }
                                        Text(
                                            text = String.format("%.2f점", detail.currentPenalty),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (detail.currentPenalty < 0) Color(0xFFE53935) else if (detail.currentPenalty > 0) Color(0xFF2E7D32) else Color.Black
                                        )
                                    }
                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                }
                            }
                            
                            // 스크롤바 가시화
                            if (scrollState.maxValue > 0) {
                                val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 2.dp, bottom = 4.dp)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                ) {
                                    val thumbHeightFraction = (300f / (300f + scrollState.maxValue)).coerceIn(0.1f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(thumbHeightFraction)
                                            .align(Alignment.TopStart)
                                            .offset(y = (300 * (1 - thumbHeightFraction) * scrollFraction).dp)
                                            .background(Color.Gray.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (itemName == "수면") "* 최근 3일간의 수면 습관이 오늘 점수에 반영됩니다." else "* 시간이 지남에 따라 감점 수치는 서서히 감소합니다.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                        )
                    }
                }

                // 물음표 버튼 및 확장형 설명
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showPenaltyInfo = !showPenaltyInfo }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "기준 안내",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "점수 산정 기준 보기",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showPenaltyInfo) {
                        val genderStr = if (healthState.gender == "Female") "여성" else "남성"
                        val infoText = when (itemName) {
                            "알코올" -> "($genderStr 기준) 24시간 내에 연속적으로 알코올을 ${if (healthState.gender == "Female") 20 else 40}g 이상 섭취하면 '폭주 패널티'가 적용되어 초과분에 대한 감점이 2배로 늘어납니다."
                            "흡연" -> "($genderStr 기준) 24시간 내에 연속적으로 담배를 ${if (healthState.gender == "Female") 7 else 13}개비 이상 피우면 '폭주 패널티'가 적용되어 초과분에 대한 감점이 2배로 늘어납니다."
                            "카페인" -> "($genderStr 기준) 24시간 내에 연속적으로 카페인을 ${if (healthState.gender == "Female") 300 else 400}mg 이상 섭취하면 '폭주 패널티'가 적용되어 초과분에 대한 감점이 2배로 늘어납니다."
                            "수면" -> "오늘, 어제, 그저께의 수면 시간을 0.5:0.3:0.2 비율로 가중 합산하여 7시간(만점 기준) 미만일 경우 점수가 계산됩니다. 꾸준한 수면 습관이 중요합니다."
                            "스크린 타임" -> "설정한 목표 시간을 초과하여 전자기기를 사용할 경우, 초과된 시간(1시간당 -1점)만큼 건강 점수에서 감점됩니다."
                            "활동시간" -> "활동시간이 목표 시간(ActiveGoals)에 미달할 경우, 부족한 시간당 -3점(분당 -0.05점)의 감점이 적용됩니다. 공식: [-3.0 × (ActiveGoals(분) - 활동시간(분)) / 60]"
                            else -> "해당 항목의 기록 수치가 건강 점수에 실시간으로 반영됩니다."
                        }
                        Surface(
                            color = Color(0xFFF0F0F0),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = infoText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (!isManualInput) {
                Text(text = "목표 $itemName 설정 (메인 화면 최대치 연동)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // 직접 입력 필드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                        value = targetInputText,
                        onValueChange = { 
                            targetInputText = it
                            val parsed = it.toFloatOrNull()
                            if (parsed != null) {
                                onTargetChange(parsed)
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        label = { Text("목표값") },
                        suffix = { Text(displayUnit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = targetValue.coerceIn(0f, targetMax), 
                    onValueChange = onTargetChange, 
                    valueRange = 0f..targetMax, 
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFD2B48C), activeTrackColor = Color(0xFFD2B48C)), 
                    modifier = Modifier.fillMaxWidth()
                )
                
                val formattedTarget = if (itemName == "활동시간") {
                    val hours = targetValue.toInt()
                    val minutes = ((targetValue - hours) * 60).roundToInt()
                    when {
                        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
                        hours > 0 -> "${hours}시간"
                        else -> "${minutes}분"
                    }
                } else {
                    String.format(java.util.Locale.getDefault(), "%.1f", targetValue)
                }
                Text(text = "$formattedTarget ${if (itemName == "활동시간") "" else displayUnit}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
