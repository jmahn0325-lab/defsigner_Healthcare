package com.example.healthcare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

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
    var tempValue by remember(value) { mutableFloatStateOf(value) }
    val dynamicMax = max(maxValue, tempValue).coerceAtLeast(1f)
    val hasChanges = tempValue > value

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                val displayValue = if (hasChanges) tempValue else value
                Text(text = "${displayValue.toInt()}$valueSuffix", fontSize = 14.sp, color = if(displayValue > maxValue) Color.Red else Color.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = tempValue.coerceIn(value, dynamicMax),
                onValueChange = { newVal ->
                    if (newVal >= value) {
                        tempValue = newVal
                    }
                },
                valueRange = 0f..dynamicMax,
                modifier = Modifier.height(24.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Max: ${maxValue.toInt()}$valueSuffix", fontSize = 10.sp, color = Color.Gray)
                if (hasChanges) {
                    Button(
                        onClick = { onValueChange(tempValue) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("저장", fontSize = 12.sp)
                    }
                }
            }
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