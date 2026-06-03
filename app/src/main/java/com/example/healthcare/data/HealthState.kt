package com.example.healthcare.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

// hour(시간) 속성이 추가되었습니다. (수동 입력은 0~23, 자동 연동 데이터는 null)
data class HealthRecord(val date: LocalDate, val hour: Int?, val type: String, val value: Float) {
    val dateTime: LocalDateTime
        get() = LocalDateTime.of(date, LocalTime.of(hour ?: 0, 0))
}

// 제품 종류 정보를 저장하는 데이터 클래스 (단위 unit 추가)
data class BeverageType(val name: String, val content: Float, val unit: String)

data class PenaltyDetail(
    val dateTime: LocalDateTime,
    val originalValue: Float,
    val currentPenalty: Float,
    val isOverThreshold: Boolean
)

class HealthState {
    var gender by mutableStateOf("남성") // "남성" 또는 "여성"
    var alcoholTarget by mutableFloatStateOf(24f)
    var smokingTarget by mutableFloatStateOf(26f)
    var caffeineTarget by mutableFloatStateOf(30f)
    var sleepTarget by mutableFloatStateOf(8f)
    var stepsTarget by mutableFloatStateOf(10000f)
    var standTarget by mutableFloatStateOf(12f)
    var screenTimeTarget by mutableFloatStateOf(6f)

    // 알코올 및 카페인 종류 리스트 (초기 단위 설정)
    val alcoholTypes = mutableStateListOf<BeverageType>(
        BeverageType("소주", 8f, "잔"), // 1잔당 알코올 약 8g
        BeverageType("맥주", 10f, "잔"), // 1잔당 알코올 약 10g
        BeverageType("와인", 10f, "잔")
    )
    val caffeineTypes = mutableStateListOf<BeverageType>(
        BeverageType("아메리카노", 150f, "잔"),
        BeverageType("에너지 드링크", 100f, "캔"),
        BeverageType("녹차", 30f, "잔")
    )

    var selectedAlcoholType by mutableStateOf(alcoholTypes[0])
    var selectedCaffeineType by mutableStateOf(caffeineTypes[0])

    private val _records = mutableStateListOf<HealthRecord>()

    init {
        val today = LocalDate.now()
        val manualTypes = listOf("알코올", "흡연", "카페인")
        for (i in 0..35) {
            val date = today.minusDays(i.toLong())
            manualTypes.forEach { type ->
                val value = when (type) {
                    "알코올" -> (0..5).random().toFloat() // 잔
                    "흡연" -> (0..10).random().toFloat() // 개비
                    "카페인" -> (0..3).random().toFloat() * 150f // mg
                    else -> 0f
                }
                // 더미 데이터 초기화 시 시간을 랜덤하게 설정 (0~23)
                val randomHour = (0..23).random()
                _records.add(HealthRecord(date, randomHour, type, value))
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

    private fun getDecay(hoursElapsed: Long): Double {
        return if (hoursElapsed >= 168) 0.0
        else (0.5).pow(hoursElapsed.toDouble() / 24.0)
    }

    private fun getCaffeineDecay(hoursElapsed: Long): Double {
        return if (hoursElapsed >= 168) 0.0
        else (0.5).pow(hoursElapsed.toDouble() / 6.0)
    }

    private fun getDailyTotalOnDate(date: LocalDate, type: String): Float {
        return _records.filter { it.date == date && it.type == type }.sumOf { it.value.toDouble() }.toFloat()
    }

    fun getTodayValue(type: String): Float {
        return getDailyTotalOnDate(LocalDate.now(), type)
    }

    // 특정 항목의 현재 총 감점 합계를 계산합니다.
    fun getTotalCurrentPenalty(type: String): Float {
        return getPenaltyDetails(type).sumOf { it.currentPenalty.toDouble() }.toFloat()
    }

    // 특정 항목의 최근 7일간의 개별 기록과 현재 적용 중인 감점 수치를 반환합니다.
    fun getPenaltyDetails(type: String): List<PenaltyDetail> {
        val now = LocalDateTime.now()
        val sevenDaysAgo = now.minusDays(7)
        val smokingThreshold = if (gender == "남성") 13f else 7f
        val alcoholThreshold = if (gender == "남성") 40f else 20f
        val caffeineThreshold = if (gender == "남성") 400f else 300f

        return _records
            .filter { it.type == type && it.dateTime.isAfter(sevenDaysAgo) }
            .sortedByDescending { it.dateTime }
            .map { record ->
                val hoursElapsed = java.time.Duration.between(record.dateTime, now).toHours()
                val dailyTotal = getDailyTotalOnDate(record.date, type)
                
                val (multiplier, decay, basePenalty) = when (type) {
                    "흡연" -> Triple(if (dailyTotal > smokingThreshold) 2.0 else 1.0, getDecay(hoursElapsed), -3.0)
                    "알코올" -> Triple(if (dailyTotal > alcoholThreshold) 2.0 else 1.0, getDecay(hoursElapsed), -0.25)
                    "카페인" -> Triple(if (dailyTotal > caffeineThreshold) 2.0 else 1.0, getCaffeineDecay(hoursElapsed), -0.04)
                    else -> Triple(1.0, 1.0, 0.0)
                }
                
                val currentPenalty = basePenalty * record.value * multiplier * decay
                PenaltyDetail(
                    dateTime = record.dateTime,
                    originalValue = record.value,
                    currentPenalty = currentPenalty.toFloat(),
                    isOverThreshold = multiplier > 1.0
                )
            }
            .filter { kotlin.math.abs(it.currentPenalty) >= 0.01f } // 감점이 0.01 미만(-0점 포함)인 기록은 제외
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
        var score = 100.0
        val now = LocalDateTime.now()
        val sevenDaysAgo = now.minusDays(7)

        // 자동 측정 데이터 기반 감점 (기존 로직 유지)
        val sleep = getTodayValue("수면")
        val stand = getTodayValue("일어서기")
        val screen = getTodayValue("스크린 타임")
        
        val sleepPenalty = if (sleep in 0.1f..sleepTarget) (sleepTarget - sleep) * 5.0 else 0.0
        val standPenalty = if (stand in 0.1f..standTarget) (standTarget - stand) * 2.0 else 0.0
        val screenPenalty = if (screen > screenTimeTarget) (screen - screenTimeTarget) * 3.0 else 0.0
        
        score -= (sleepPenalty + standPenalty + screenPenalty)

        // 수동 입력 데이터 기반 감점 (신규 감쇠 로직 적용)
        val smokingThreshold = if (gender == "남성") 13f else 7f
        val alcoholThreshold = if (gender == "남성") 40f else 20f
        val caffeineThreshold = if (gender == "남성") 400f else 300f

        val relevantRecords = _records.filter { it.dateTime.isAfter(sevenDaysAgo) }

        relevantRecords.forEach { record ->
            val hoursElapsed = java.time.Duration.between(record.dateTime, now).toHours()
            if (hoursElapsed < 0) return@forEach // 미래 데이터 무시

            when (record.type) {
                "흡연" -> {
                    val dailyTotal = getDailyTotalOnDate(record.date, "흡연")
                    val multiplier = if (dailyTotal > smokingThreshold) 2.0 else 1.0
                    val decay = getDecay(hoursElapsed)
                    score += -3.0 * record.value * multiplier * decay
                }
                "알코올" -> {
                    val dailyTotal = getDailyTotalOnDate(record.date, "알코올")
                    val multiplier = if (dailyTotal > alcoholThreshold) 2.0 else 1.0
                    val decay = getDecay(hoursElapsed)
                    score += -2.5 * (record.value / 10.0) * multiplier * decay
                }
                "카페인" -> {
                    // record.value는 'mg' 단위 (단, 더미 데이터는 아메리카노 10 기준이었으므로 확인 필요)
                    val dailyTotal = getDailyTotalOnDate(record.date, "카페인")
                    val multiplier = if (dailyTotal > caffeineThreshold) 2.0 else 1.0
                    val decay = getCaffeineDecay(hoursElapsed)
                    score += -0.04 * record.value * multiplier * decay
                }
            }
        }

        return score.toInt().coerceIn(0, 100)
    }

    // 가장 감점이 큰 요소를 찾아 맞춤형 피드백을 제공합니다.
    fun getHealthFeedback(): String {
        val sleep = getTodayValue("수면")
        val stand = getTodayValue("일어서기")
        val screen = getTodayValue("스크린 타임")
        val alcohol = getTodayValue("알코올") / selectedAlcoholType.content
        val smoke = getTodayValue("흡연")
        val caffeine = getTodayValue("카페인") / selectedCaffeineType.content

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
