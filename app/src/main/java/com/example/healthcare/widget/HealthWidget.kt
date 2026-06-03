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
import com.example.healthcare.data.HealthState
import java.time.LocalDate
import java.util.Locale

class AlcoholWidget : HealthWidget("알코올")
class SmokingWidget : HealthWidget("흡연")
class CaffeineWidget : HealthWidget("카페인")

open class HealthWidget(val type: String) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val healthState = HealthState.getInstance()
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
        val healthState = HealthState.getInstance()
        val currentValue = healthState.getTodayValue(type)
        
        val incrementAmount = when (type) {
            "알코올" -> healthState.selectedAlcoholType.content
            "카페인" -> healthState.selectedCaffeineType.content
            else -> 1f
        }
        
        healthState.updateRecord(LocalDate.now(), type, currentValue + incrementAmount)
        
        // 모든 위젯을 갱신합니다.
        HealthWidget.updateAllWidgets(context)
    }
}
