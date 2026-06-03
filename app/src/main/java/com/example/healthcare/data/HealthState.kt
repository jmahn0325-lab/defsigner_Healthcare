package com.example.healthcare.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// hour(시간) 속성이 추가되었습니다. (수동 입력은 0~23, 자동 연동 데이터는 null)
data class HealthRecord(val date: LocalDate, val hour: Int?, val type: String, val value: Float)

// 제품 종류 정보를 저장하는 데이터 클래스 (단위 unit 추가)
data class BeverageType(val name: String, val content: Float, val unit: String)

// 감점 상세 정보를 저장하는 데이터 클래스
data class PenaltyDetail(
    val dateTime: LocalDateTime,
    val originalValue: Float,
    val isOverThreshold: Boolean,
    val currentPenalty: Float
)

class HealthState {
    var alcoholTarget by mutableFloatStateOf(24f)
    var smokingTarget by mutableFloatStateOf(26f)
    var caffeineTarget by mutableFloatStateOf(30f)
    var sleepTarget by mutableFloatStateOf(8f)
    var stepsTarget by mutableFloatStateOf(10000f)
    var standTarget by mutableFloatStateOf(12f)
    var screenTimeTarget by mutableFloatStateOf(6f)

    // 알코올 및 카페인 종류 리스트 (초기 단위 설정)
    val alcoholTypes = mutableStateListOf<BeverageType>(
        BeverageType("소주", 1f, "잔"),
        BeverageType("맥주", 1f, "잔"),
        BeverageType("와인", 1f, "잔")
    )
    val caffeineTypes = mutableStateListOf<BeverageType>(
        BeverageType("아메리카노", 10f, "잔"),
        BeverageType("에너지 드링크", 50f, "캔"),
        BeverageType("녹차", 5f, "잔")
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
                    "알코올" -> (0..5).random().toFloat() * 1f // g (기본 함유량 1 기준)
                    "흡연" -> (0..10).random().toFloat()
                    "카페인" -> (0..3).random().toFloat() * 10f // mg (아메리카노 10 기준)
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

        // 자동 측정 항목 감점 (가중치 적용)
        val sleepPenalty = if (sleep in 0.1f..sleepTarget) (sleepTarget - sleep) * 5f else 0f
        val standPenalty = if (stand in 0.1f..standTarget) (standTarget - stand) * 2f else 0f
        val screenPenalty = if (screen > screenTimeTarget) (screen - screenTimeTarget) * 3f else 0f
        
        // 수동 입력 항목 감점 (시간 감쇠가 적용된 현재 총 감점 합계)
        val alcoholPenalty = getTotalCurrentPenalty("알코올")
        val smokePenalty = getTotalCurrentPenalty("흡연")
        val caffeinePenalty = getTotalCurrentPenalty("카페인")

        // score += penalty 인 이유는 getTotalCurrentPenalty가 음수 값을 반환하기 때문입니다.
        score -= (sleepPenalty + standPenalty + screenPenalty)
        score += (alcoholPenalty + smokePenalty + caffeinePenalty)
        
        return Math.round(score).toInt().coerceIn(0, 100)
    }

    // 가장 감점이 큰 요소를 찾아 맞춤형 피드백을 제공합니다.
    fun getHealthFeedback(): String {
        val sleep = getTodayValue("수면")
        val stand = getTodayValue("일어서기")
        val screen = getTodayValue("스크린 타임")

        val penalties = mutableMapOf(
            "수면" to if (sleep in 0.1f..sleepTarget) (sleepTarget - sleep) * 5f else 0f,
            "일어서기" to if (stand in 0.1f..standTarget) (standTarget - stand) * 2f else 0f,
            "스크린 타임" to if (screen > screenTimeTarget) (screen - screenTimeTarget) * 3f else 0f
        )
        
        listOf("알코올", "흡연", "카페인").forEach { 
            penalties[it] = -getTotalCurrentPenalty(it)
        }

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

    // --- 추가된 메서드들 ---

    fun saveSelection(type: String, name: String) {
        // 실제 구현에서는 SharedPreferences 등을 사용할 수 있습니다.
    }

    fun loadSelections(context: Context) {
        // 초기화 시 호출
    }

    fun getTotalCurrentPenalty(type: String): Float {
        return getPenaltyDetails(type).sumOf { it.currentPenalty.toDouble() }.toFloat()
    }

    fun getPenaltyDetails(type: String): List<PenaltyDetail> {
        val now = LocalDateTime.now()
        val sevenDaysAgo = now.minusDays(7)
        val manualRecords = _records.filter { it.type == type && it.hour != null && it.value > 0f }
            .sortedWith(compareBy({ it.date }, { it.hour }))
        
        var sessionIntake = 0f // 세션 내 누적 섭취량 (임계치 체크용)
        var lastIntakeTime: LocalDateTime? = null

        return manualRecords.mapNotNull { record ->
            // 시점 스냅: 해당 시간의 정시(00분) 기준으로 처리 (1시 10분/50분 모두 1시 데이터)
            val recordDateTime = record.date.atTime(record.hour ?: 0, 0)
            
            // 세션 리셋 로직: 마지막 섭취로부터 24시간 동안 공백이 있었는지 확인
            if (lastIntakeTime != null && ChronoUnit.HOURS.between(lastIntakeTime, recordDateTime) >= 24) {
                sessionIntake = 0f
            }
            
            val threshold = when (type) {
                "알코올" -> 40f  // 40g
                "카페인" -> 400f // 400mg
                "흡연" -> 13f    // 13개비 (요청사항 반영)
                else -> 1000f
            }

            // 가중치 설정 (요청하신 고정 가중치)
            val baseWeight = when (type) {
                "알코올" -> 0.25f // 10g당 2.5점 (1g당 0.25점)
                "흡연" -> 3.0f    // 1개비당 3.0점
                "카페인" -> 0.04f  // 50mg당 2.0점 (1mg당 0.04점)
                else -> 0f
            }

            // 폭주 페널티 분할 계산: 임계치 이전분(1배)과 이후분(2배)을 나눔
            val normalPart = (threshold - sessionIntake).coerceIn(0f, record.value)
            val bingePart = (record.value - normalPart).coerceAtLeast(0f)
            val wasAlreadyBinging = sessionIntake >= threshold

            // 이산적 시간 경과 계산 (정시 기준 차이)
            val totalHoursPassed = ChronoUnit.HOURS.between(recordDateTime, now).toFloat()
            if (totalHoursPassed < 0) return@mapNotNull null

            // 지수 감쇠 적용 (반감기 수식): 알코올/흡연 24시간, 카페인 6시간
            val halfLife = if (type == "카페인") 6.0 else 24.0
            val decayFactor = Math.pow(0.5, totalHoursPassed / halfLife).toFloat()

            // 포인트 계산: (일반분량 * 1배 + 폭주분량 * 2배) * 감쇠계수
            val penaltyPoints = ((normalPart * baseWeight) + (bingePart * baseWeight * 2.0f)) * decayFactor
            val currentPenalty = -penaltyPoints
            
            // 디버깅을 위해 결과가 비정상적인지 체크 (예: 카페인이 너무 크게 감점되는지)
            // sessionIntake 업데이트 전 현재 상태 기록
            val isBingeRecord = wasAlreadyBinging || bingePart > 0f

            // 세션 데이터 업데이트 (리셋 여부와 상관없이 누적 및 시간 갱신)
            sessionIntake += record.value
            lastIntakeTime = recordDateTime

            // UI 표시용으로는 최근 7일 데이터만 반환
            if (recordDateTime.isBefore(sevenDaysAgo)) return@mapNotNull null

            PenaltyDetail(
                dateTime = recordDateTime,
                originalValue = record.value,
                isOverThreshold = isBingeRecord,
                currentPenalty = currentPenalty
            )
        }
    }

    fun updateRecord(date: LocalDate, type: String, value: Float) {
        updateAutoRecord(date, type, value)
    }

    companion object {
        @Volatile
        private var instance: HealthState? = null

        fun getInstance(context: Context? = null): HealthState {
            return instance ?: synchronized(this) {
                instance ?: HealthState().also { 
                    instance = it 
                    context?.let { ctx -> it.loadSelections(ctx) }
                }
            }
        }
    }
}
