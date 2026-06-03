package com.example.healthcare.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AlcoholWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AlcoholWidget()
}

class SmokingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SmokingWidget()
}

class CaffeineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CaffeineWidget()
}
