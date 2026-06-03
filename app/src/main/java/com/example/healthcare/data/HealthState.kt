package com.example.healthcare.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// hour(시간) 속성이 추가되었습니다. (수동 입력은 0~23, 자동 연동 데이터는 null)
data class HealthRecord(val date: LocalDate, val hour: Int?, val type: String, val value: Float)

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
                // 더미 데이터 초기화
                _records.add(HealthRecord(date, 12, type, value))
            }
        }
    }

    // 자동 연동 데이터 (수면, 걸음수 등)는 기존 값을 지우고 하루 전체의 갱신값으로 덮어씌웁니다.
    fun updateAutoRecord(date: LocalDate, type: String, value: Float) {
        _records.removeAll { it.date == date && it.type == type }
        _records.add(HealthRecord(date, null, type, value))
    }

    // 수동 입력 데이터 (알코올, 흡연 등)는 기존 총량과의 차이(Delta)를 구해 특정 시간에 누적합니다.
    // 섭취량 기록의 특성상 줄이는 것은 허용하지 않습니다.
    fun updateManualRecord(date: LocalDate, hour: Int, type: String, newValue: Float) {
        val currentTotal = getTodayValue(type)
        val delta = newValue - currentTotal
        if (delta > 0f) {
            _records.add(HealthRecord(date, hour, type, delta))
        }
    }

    fun getTodayValue(type: String): Float {
        return _records.filter { it.date == LocalDate.now() && it.type == type }.sumOf { it.value.toDouble() }.toFloat()
    }

    fun getChartData(type: String, period: String): List<Pair<String, Float>> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("M/d")

        return if (period == "단위(일)") {
            (4 downTo 0).map { i ->
                val date = today.minusDays(i.toLong())
                val value = _records.filter { it.date == date && it.type == type }.sumOf { it.value.toDouble() }.toFloat()
                Pair(date.format(formatter), value)
            }
        } else {
            (4 downTo 0).map { i ->
                val weekStart = today.minusWeeks(i.toLong()).with(DayOfWeek.MONDAY)
                var weeklySum = 0f
                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date <= today) {
                        weeklySum += _records.filter { it.date == date && it.type == type }.sumOf { it.value.toDouble() }.toFloat()
                    }
                }
                Pair("${weekStart.format(formatter)}~", weeklySum)
            }
        }
    }

    // 걸음수를 제외한 각 건강 지표를 통해 0~100점의 종합 점수를 계산합니다.
    fun getHealthScore(): Int {
        var score = 100f
        val sleep = getTodayValue("수면")
        val stand = getTodayValue("일어서기")
        val screen = getTodayValue("스크린 타임")
        val alcohol = getTodayValue("알코올")
        val smoke = getTodayValue("흡연")
        val caffeine = getTodayValue("카페인")

        // 항목별 감점 로직 (가중치 적용)
        val sleepPenalty = if (sleep in 0.1f..sleepTarget) (sleepTarget - sleep) * 5f else 0f
        val standPenalty = if (stand in 0.1f..standTarget) (standTarget - stand) * 2f else 0f
        val screenPenalty = if (screen > screenTimeTarget) (screen - screenTimeTarget) * 3f else 0f
        val alcoholPenalty = alcohol * 3f  // 1잔당 -3점
        val smokePenalty = smoke * 4f      // 1개비당 -4점
        val caffeinePenalty = if (caffeine > caffeineTarget) (caffeine - caffeineTarget) * 2f else 0f

        score -= (sleepPenalty + standPenalty + screenPenalty + alcoholPenalty + smokePenalty + caffeinePenalty)
        return score.toInt().coerceIn(0, 100)
    }

    // 가장 감점이 큰 요소를 찾아 맞춤형 피드백을 제공합니다.
    fun getHealthFeedback(): String {
        val sleep = getTodayValue("수면")
        val stand = getTodayValue("일어서기")
        val screen = getTodayValue("스크린 타임")
        val alcohol = getTodayValue("알코올")
        val smoke = getTodayValue("흡연")
        val caffeine = getTodayValue("카페인")

        val penalties = mapOf(
            "수면" to if (sleep in 0.1f..sleepTarget) (sleepTarget - sleep) * 5f else 0f,
            "일어서기" to if (stand in 0.1f..standTarget) (standTarget - stand) * 2f else 0f,
            "스크린 타임" to if (screen > screenTimeTarget) (screen - screenTimeTarget) * 3f else 0f,
            "알코올" to alcohol * 3f,
            "흡연" to smoke * 4f,
            "카페인" to if (caffeine > caffeineTarget) (caffeine - caffeineTarget) * 2f else 0f
        )

        val worstFactor = penalties.maxByOrNull { it.value }

        if (worstFactor == null || worstFactor.value == 0f) {
            return "건강한 하루를 보내고 계시네요! 지금처럼만 유지하세요."
        }

        return when (worstFactor.key) {
            "수면" -> "수면이 부족합니다. 뇌와 신체가 쉴 수 있도록 오늘 밤은 일찍 주무세요."
            "일어서기" -> "오래 앉아 계셨네요. 중간중간 일어나서 스트레칭을 해볼까요?"
            "스크린 타임" -> "전자기기 사용 시간이 깁니다. 눈과 뇌에 휴식을 주세요."
            "알코올" -> "음주량이 높습니다. 간이 회복할 수 있도록 오늘은 술을 참아보세요."
            "흡연" -> "흡연량이 많아 건강에 치명적입니다. 단기 금연부터 시도해보는 것은 어떨까요?"
            "카페인" -> "카페인 섭취가 높습니다. 커피 대신 물을 많이 마셔보세요."
            else -> "건강 수치에 주의가 필요합니다."
        }
    }
}