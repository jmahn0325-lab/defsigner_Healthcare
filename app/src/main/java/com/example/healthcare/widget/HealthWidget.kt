package com.example.healthcare.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.content.Intent
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.appwidget.action.actionStartActivity
import com.example.healthcare.MainActivity
import com.example.healthcare.data.HealthState
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class AlcoholWidget : HealthWidget("알코올")
class SmokingWidget : HealthWidget("흡연")
class CaffeineWidget : HealthWidget("카페인")

class SpectrumWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val healthState = HealthState.getInstance(context)
        healthState.loadFromStorage() // 렌더링 전 최신 데이터 강제 로드
        val score = healthState.getHealthScore()
        val feedback = healthState.getHealthFeedback()
        val emoji = when {
            score >= 80 -> "😏"
            score >= 50 -> "😐"
            else -> "😫"
        }
        val gaugeColor = when {
            score >= 80 -> Color(0xFF4CAF50) // Green
            score >= 50 -> Color(0xFFFF9800) // Orange
            else -> Color(0xFFF44336)        // Red
        }

        provideContent {
            Box(modifier = GlanceModifier.fillMaxSize().background(Color.White)) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 이모지 영역
                    Box(
                        modifier = GlanceModifier
                            .size(48.dp)
                            .background(Color.White)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, style = TextStyle(fontSize = 32.sp))
                    }
                    
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    
                    // 점수 및 피드백 영역
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$score",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            
                            // 프로그레스 바 (점수에 따라 색상 변경, 두께 증가)
                            LinearProgressIndicator(
                                progress = score / 100f,
                                modifier = GlanceModifier.defaultWeight().height(16.dp),
                                color = ColorProvider(gaugeColor, gaugeColor),
                                backgroundColor = ColorProvider(Color.LightGray, Color.LightGray)
                            )
                            
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(text = "100", style = TextStyle(fontSize = 10.sp))
                        }
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = feedback,
                            style = TextStyle(fontSize = 11.sp),
                            maxLines = 2
                        )
                    }
                }

                // 우측 상단 새로고침 버튼
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(32.dp)
                            .padding(4.dp)
                            .clickable(actionRunCallback<RefreshAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔄", style = TextStyle(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val healthState = HealthState.getInstance(context)
        healthState.loadFromStorage()
        HealthWidget.updateAllWidgets(context)
    }
}

open class HealthWidget(val type: String) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val healthState = HealthState.getInstance(context)
        healthState.loadFromStorage() // 렌더링 전 최신 데이터 강제 로드
        val emoji = when (type) {
            "알코올" -> "🍺"
            "흡연" -> "🚬"
            "카페인" -> "☕"
            else -> "❓"
        }
        
        val selectedBeverage = when (type) {
            "알코올" -> healthState.selectedAlcoholType
            "카페인" -> healthState.selectedCaffeineType
            else -> null
        }
        
        val unit = selectedBeverage?.unit ?: if (type == "흡연") "개비" else ""
        val beverageName = selectedBeverage?.name

        provideContent {
            val totalValue = healthState.getTodayValue(type)
            val content = selectedBeverage?.content ?: 1f
            val displayValue = if (type == "흡연") totalValue.toInt() else (totalValue / content)
            val formattedValue = if (type == "흡연") "$displayValue" else String.format(Locale.getDefault(), "%.1f", displayValue)
            
            WidgetContent(type, beverageName, emoji, formattedValue, unit)
        }
    }

    @Composable
    private fun WidgetContent(type: String, beverageName: String?, emoji: String, value: String, unit: String) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .padding(8.dp)
                .clickable(actionRunCallback<IncrementAction>(
                    actionParametersOf(typeKey to type)
                )),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = emoji, style = TextStyle(fontSize = 32.sp))
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = type,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = ColorProvider(Color.Black, Color.White)
                    )
                )
                if (beverageName != null) {
                    Text(
                        text = beverageName,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ColorProvider(Color.Gray, Color.LightGray)
                        )
                    )
                }
            }
            Text(
                text = "$value$unit",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorProvider(Color.DarkGray, Color.LightGray)
                )
            )
        }
    }

    companion object {
        val typeKey = ActionParameters.Key<String>("type")

        suspend fun updateAllWidgets(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(AlcoholWidget::class.java).forEach { AlcoholWidget().update(context, it) }
            manager.getGlanceIds(SmokingWidget::class.java).forEach { SmokingWidget().update(context, it) }
            manager.getGlanceIds(CaffeineWidget::class.java).forEach { CaffeineWidget().update(context, it) }
            manager.getGlanceIds(SpectrumWidget::class.java).forEach { SpectrumWidget().update(context, it) }
        }
    }
}

class IncrementAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val type = parameters[HealthWidget.typeKey] ?: return
        val healthState = HealthState.getInstance(context)
        
        val incrementAmount = when (type) {
            "알코올" -> healthState.selectedAlcoholType.content
            "카페인" -> healthState.selectedCaffeineType.content
            else -> 1f
        }
        
        val manualTypes = listOf("알코올", "흡연", "카페인")
        if (type in manualTypes) {
            val currentValue = healthState.getTodayValue(type)
            val now = LocalTime.now()
            healthState.updateManualRecord(
                LocalDate.now(), 
                now.hour, 
                now.minute, 
                now.second,
                type, 
                currentValue + incrementAmount,
                if (type == "알코올") healthState.selectedAlcoholType.name else if (type == "카페인") healthState.selectedCaffeineType.name else if (type == "흡연") "담배" else null,
                if (type == "알코올") healthState.selectedAlcoholType.unit else if (type == "카페인") healthState.selectedCaffeineType.unit else if (type == "흡연") "개비" else null
            )
        } else {
            val currentValue = healthState.getTodayValue(type)
            healthState.updateAutoRecord(LocalDate.now(), type, currentValue + incrementAmount)
        }
        
        // 데이터가 파일에 완전히 기록될 시간을 벌기 위해 아주 짧은 지연 후 갱신을 시도합니다.
        kotlinx.coroutines.delay(100)
        HealthWidget.updateAllWidgets(context)
    }
}
