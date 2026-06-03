
package com.example.healthcare.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class HealthRecord(val date: LocalDate, val type: String, val value: Float)

class HealthState {
    var alcoholTarget by mutableFloatStateOf(10f)
    var smokingTarget by mutableFloatStateOf(20f)
    var caffeineTarget by mutableFloatStateOf(10f)
    var sleepTarget by mutableFloatStateOf(8f)
    var stepsTarget by mutableFloatStateOf(10000f)
    var standTarget by mutableFloatStateOf(12f)
    var screenTimeTarget by mutableFloatStateOf(6f)

    private val _records = mutableStateListOf<HealthRecord>()

    init {
        val today = LocalDate.now()
        val manualTypes = listOf("알코올", "흡연", "카페인")
        for (i in 0..35) {
            val date = today.minusDays(i.toLong())
            manualTypes.forEach { type ->
                val value = when (type) {
                    "알코올" -> (0..5).random().toFloat()
                    "흡연" -> (0..10).random().toFloat()
                    "카페인" -> (0..3).random().toFloat()
                    else -> 0f
                }
                _records.add(HealthRecord(date, type, value))
            }
        }
    }

    fun updateRecord(date: LocalDate, type: String, value: Float) {
        _records.removeAll { it.date == date && it.type == type }
        _records.add(HealthRecord(date, type, value))
    }

    fun getTodayValue(type: String): Float {
        return _records.find { it.date == LocalDate.now() && it.type == type }?.value ?: 0f
    }

    fun getChartData(type: String, period: String): List<Pair<String, Float>> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("M/d")

        return if (period == "단위(일)") {
            (4 downTo 0).map { i ->
                val date = today.minusDays(i.toLong())
                val value = _records.find { it.date == date && it.type == type }?.value ?: 0f
                Pair(date.format(formatter), value)
            }
        } else {
            (4 downTo 0).map { i ->
                val weekStart = today.minusWeeks(i.toLong()).with(DayOfWeek.MONDAY)
                var weeklySum = 0f
                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date <= today) {
                        weeklySum += _records.find { it.date == date && it.type == type }?.value ?: 0f
                    }
                }
                Pair("${weekStart.format(formatter)}~", weeklySum)
            }
        }
    }
}