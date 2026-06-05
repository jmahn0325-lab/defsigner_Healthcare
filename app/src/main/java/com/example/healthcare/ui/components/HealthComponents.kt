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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TopSpectrumBanner(score: Int = 70, message: String = "수면은 충분하지만, 어제 음주를 많이 하셨네요.\n술을 줄이고 물을 많이 마셔볼까요?", onClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black).clickable { onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val emoji = when {
                score >= 80 -> "😏"
                score >= 50 -> "😐"
                else -> "😫"
            }
            Box(modifier = Modifier.size(60.dp).border(1.dp, Color.Black, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text(text = emoji, fontSize = 32.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "$score", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    // 그라데이션 히트바 구현
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .padding(bottom = 6.dp)
                            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    ) {
                        val gradientBrush = Brush.horizontalGradient(
                            colors = listOf(Color.Red, Color.Yellow, Color.Green)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(score / 100f)
                                .fillMaxHeight()
                                .background(gradientBrush, RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "100", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(text = message, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(imageVector = Icons.Default.Info, contentDescription = "상세 정보", modifier = Modifier.align(Alignment.Top))
        }
    }
}

@Composable
fun HealthInputSlider(emoji: String, title: String, valueSuffix: String, value: Float, maxValue: Float, onClick: () -> Unit = {}) {
    val progress = (value / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val color = if (value > maxValue) Color.Red else MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).clickable { onClick() }.padding(12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = "${value.toInt()}$valueSuffix", fontSize = 14.sp, color = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).border(1.dp, Color.LightGray),
                color = color,
                trackColor = Color.Transparent
            )
            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
fun CustomBarChart(
    data: List<Pair<String, Float>>,
    isWeekly: Boolean,
    modifier: Modifier = Modifier,
    yAxisTitle: String = "",
    targetValue: Float? = null,
    isHigherBetter: Boolean = true
) {
    val rawMax = data.maxOfOrNull { it.second } ?: 10f
    val chartMax = if (targetValue != null) maxOf(rawMax, targetValue) else rawMax
    val maxDataValue = (chartMax * 1.2f).coerceAtLeast(10f)
    val yAxisLabels = listOf(maxDataValue.toInt().toString(), (maxDataValue / 2).toInt().toString(), "0")

    // 항목 특성에 따른 색상 및 문구 설정
    val themeColor = if (isHigherBetter) Color(0xFF4CAF50) else Color(0xFFE53935)
    val goalSuffix = if (isHigherBetter) " 이상" else " 이하"

    Column(modifier = modifier) {
        if (yAxisTitle.isNotEmpty()) {
            Text(
                text = "($yAxisTitle)",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            // 배경 가이드 라인
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                yAxisLabels.forEach { label ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.width(40.dp), textAlign = TextAlign.End, maxLines = 1)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
                    }
                }
            }

            // 목표량 영역 강조 및 수치 표시
            if (targetValue != null && targetValue > 0) {
                val targetHeightFraction = (targetValue / maxDataValue).coerceIn(0f, 1f)
                
                // 1. 강조 배경 영역
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(targetHeightFraction)
                        .align(Alignment.BottomEnd)
                        .padding(start = 48.dp, bottom = 20.dp)
                        .background(themeColor.copy(alpha = 0.12f))
                )
                
                // 2. 목표선 및 수치 텍스트
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(targetHeightFraction)
                        .align(Alignment.BottomEnd)
                        .padding(start = 48.dp, bottom = 20.dp)
                ) {
                    // 목표 수평선
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(themeColor.copy(alpha = 0.4f)).align(Alignment.TopStart))
                    
                    // 목표치 텍스트 라벨 (왼쪽 배치)
                    val formattedTarget = if (targetValue == targetValue.toInt().toFloat()) "${targetValue.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", targetValue)
                    Text(
                        text = "목표: $formattedTarget$yAxisTitle$goalSuffix",
                        fontSize = 9.sp,
                        color = themeColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, top = 24.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                data.forEach { (_, value) ->
                    val heightFraction = (value / maxDataValue).coerceIn(0f, 1f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                        Text(text = if (value == value.toInt().toFloat()) "${value.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", value), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
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
}
