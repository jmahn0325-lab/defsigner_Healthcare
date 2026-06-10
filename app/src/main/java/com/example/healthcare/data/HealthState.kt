package com.example.healthcare.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// hour(시간) 속성이 추가되었습니다. (수동 입력은 0~23, 자동 연동 데이터는 null)
data class HealthRecord(
    val date: LocalDate,
    val hour: Int?,
    val minute: Int? = null,
    val second: Int? = null,
    val type: String,
    val value: Float,
    val itemName: String? = null,
    val unit: String? = null,
    val isBinge: Boolean = false // 추가: 폭주 패널티 여부 저장
)

// 제품 종류 정보를 저장하는 데이터 클래스 (단위 unit 추가)
data class BeverageType(val name: String, val content: Float, val unit: String)

// 감점 상세 정보를 저장하는 데이터 클래스
data class PenaltyDetail(
    val dateTime: LocalDateTime,
    val originalValue: Float,
    val isOverThreshold: Boolean,
    val currentPenalty: Float,
    val itemName: String? = null,
    val unit: String? = null,
    val minute: Int? = null // 추가: 실제 입력 분
)

/** 대학생 맞춤형 알림 시스템을 위한 상태 정의 **/
enum class HealthFactorType(val displayName: String, val impactWeight: Int) {
    SLEEP("수면", 10),
    SMOKING("흡연", 9),
    ALCOHOL("알코올", 9),
    CAFFEINE("카페인", 7),
    ACTIVITY("활동하기", 6),
    SCREEN_TIME("스크린 타임", 5)
}

enum class HealthStatus(val priority: Int) {
    DANGER(2),      // 위험 (>110% or 특수기준)
    CAUTION(1),     // 주의 (80~110% or 특수기준)
    NORMAL(0),      // 보통
    FAIR(0),        // 양호
    GOOD(0)         // 좋음
}

data class HealthFactor(
    val type: HealthFactorType,
    val value: Float,
    val target: Float,
    val hasRecentBinge: Boolean = false,
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    val status: HealthStatus
        get() {
            val baseStatus = when (type) {
                HealthFactorType.SLEEP -> {
                    if (value < 5.0f) HealthStatus.DANGER
                    else if (value < 5.8f) HealthStatus.CAUTION
                    else if (value < 6.3f) HealthStatus.NORMAL
                    else if (value < 6.8f) HealthStatus.FAIR
                    else HealthStatus.GOOD
                }
                HealthFactorType.ACTIVITY -> {
                    val ratio = value / target.coerceAtLeast(0.1f)
                    if (ratio <= 0.3f) HealthStatus.DANGER
                    else if (ratio <= 0.6f) HealthStatus.CAUTION
                    else if (ratio <= 0.8f) HealthStatus.NORMAL
                    else if (ratio <= 1.1f) HealthStatus.FAIR
                    else HealthStatus.GOOD
                }
                HealthFactorType.SCREEN_TIME -> {
                    val ratio = value / target.coerceAtLeast(0.1f)
                    if (ratio > 1.6f) HealthStatus.DANGER
                    else if (ratio > 1.4f) HealthStatus.CAUTION
                    else if (ratio > 1.2f) HealthStatus.NORMAL
                    else if (ratio > 0.8f) HealthStatus.FAIR
                    else HealthStatus.GOOD
                }
                else -> {
                    val ratio = value / target.coerceAtLeast(0.1f)
                    if (ratio > 1.1f) HealthStatus.DANGER
                    else if (ratio > 0.8f) HealthStatus.CAUTION
                    else if (ratio > 0.6f) HealthStatus.NORMAL
                    else if (ratio > 0.3f) HealthStatus.FAIR
                    else HealthStatus.GOOD
                }
            }
            
            // 최근 1일 이내 폭주 이력이 있다면 '보통' 이하일 경우 '주의'로 격상
            return if (hasRecentBinge && (baseStatus == HealthStatus.NORMAL || baseStatus == HealthStatus.FAIR || baseStatus == HealthStatus.GOOD)) {
                HealthStatus.CAUTION
            } else {
                baseStatus
            }
        }

    val ratioValue: Float get() = value / target.coerceAtLeast(0.1f)
}

class HealthState private constructor(private val context: Context?) {
    var userId by mutableStateOf("guest_user")
    var userName by mutableStateOf("")
    var bio by mutableStateOf("") // 소개글 추가
    var isPublic by mutableStateOf(true) // 공개 여부 추가
    var gender by mutableStateOf("Male")
    var isOnboardingCompleted by mutableStateOf(false)

    var alcoholTarget by mutableFloatStateOf(24f)
    var smokingTarget by mutableFloatStateOf(26f)
    var caffeineTarget by mutableFloatStateOf(30f)
    var sleepTarget by mutableFloatStateOf(8f)
    private var _activityTarget by mutableFloatStateOf(2.0f)
    var activityTarget: Float
        get() = _activityTarget
        set(value) {
            _activityTarget = value
            saveProfile()
        }
    var screenTimeTarget by mutableFloatStateOf(6f)

    // 로컬 파티 별명 관리를 위한 관찰 가능한 맵
    private val _localPartyNames = androidx.compose.runtime.mutableStateMapOf<String, String>()

    // 알코올 및 카페인 종류 리스트 (초기 단위 설정)
    val alcoholTypes = mutableStateListOf(
        BeverageType("소주", 6.3f, "잔"),
        BeverageType("맥주", 7.1f, "잔"),
        BeverageType("와인", 15.4f, "잔")
    )
    val caffeineTypes = mutableStateListOf(
        BeverageType("아메리카노", 150f, "잔"),
        BeverageType("에너지 드링크", 100f, "캔"),
        BeverageType("녹차", 30f, "잔")
    )

    var selectedAlcoholType by mutableStateOf(alcoholTypes[0])
    var selectedCaffeineType by mutableStateOf(caffeineTypes[0])

    private val _records = mutableStateListOf<HealthRecord>()
    val allRecords: List<HealthRecord> get() = _records.toList()

    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

    init {
        loadSelections()
        createDummyData() // 실행 시마다 항상 100점으로 시작하는 더미 데이터 생성
        saveToStorage()
    }

    private fun createDummyData() {
        _records.clear()
        val today = LocalDate.now()
        
        // 지난 35일간의 데이터 생성
        for (i in 0..35) {
            val date = today.minusDays(i.toLong())
            
            // 오늘(0)부터 최근 2일까지는 100점을 위해 '완벽한' 데이터 입력
            // (수면 점수 가중치 평균을 위해 최근 3일치가 중요함)
            if (i <= 2) {
                _records.add(HealthRecord(date = date, hour = null, type = "수면", value = 7.0f)) // 이 수식에서 7시간이 만점 기준
                _records.add(HealthRecord(date = date, hour = null, type = "활동시간", value = activityTarget))
                _records.add(HealthRecord(date = date, hour = null, type = "스크린 타임", value = screenTimeTarget))
                _records.add(HealthRecord(date = date, hour = null, type = "걸음수", value = 10000f))
                // 수동 입력 항목은 0으로 설정하여 감점 방지
                _records.add(HealthRecord(date = date, hour = 12, type = "알코올", value = 0f))
                _records.add(HealthRecord(date = date, hour = 12, type = "흡연", value = 0f))
                _records.add(HealthRecord(date = date, hour = 12, type = "카페인", value = 0f))
            } else {
                // 그 외 과거 데이터는 차트를 위해 랜덤 생성
                _records.add(HealthRecord(date = date, hour = null, type = "수면", value = (5..8).random().toFloat()))
                _records.add(HealthRecord(date = date, hour = null, type = "활동시간", value = (1..3).random().toFloat()))
                _records.add(HealthRecord(date = date, hour = null, type = "스크린 타임", value = (3..7).random().toFloat()))
                _records.add(HealthRecord(date = date, hour = 12, type = "알코올", value = (0..2).random().toFloat() * 5f))
                _records.add(HealthRecord(date = date, hour = 15, type = "흡연", value = (0..3).random().toFloat()))
                _records.add(HealthRecord(date = date, hour = 10, type = "카페인", value = (0..2).random().toFloat() * 100f))
                _records.add(HealthRecord(date = date, hour = null, type = "걸음수", value = (3000..12000).random().toFloat()))
            }
        }
    }

    private fun getRecordsFile(): java.io.File? = context?.filesDir?.let { java.io.File(it, "health_records.json") }

    private fun saveToStorage() {
        val jsonArray = JSONArray()
        _records.forEach { record ->
            val obj = JSONObject()
            obj.put("date", record.date.toString())
            obj.put("hour", record.hour ?: -1)
            obj.put("minute", record.minute ?: -1)
            obj.put("second", record.second ?: -1)
            obj.put("type", record.type)
            obj.put("value", record.value.toDouble())
            obj.put("isBinge", record.isBinge) // 저장
            record.itemName?.let { obj.put("itemName", it) }
            record.unit?.let { obj.put("unit", it) }
            jsonArray.put(obj)
        }
        
        try {
            getRecordsFile()?.writeText(jsonArray.toString())
        } catch (_: Exception) { }
    }

    // 파일 시스템에서 직접 데이터를 읽어와 프로세스 간 캐시 문제를 방지합니다.
    fun loadFromStorage(): Boolean {
        val file = getRecordsFile() ?: return false
        if (!file.exists()) return false
        
        val data = try { file.readText() } catch (_: Exception) { return false }
        if (data.isBlank()) return false
        
        try {
            val jsonArray = JSONArray(data)
            val newRecords = mutableListOf<HealthRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val hourVal = obj.getInt("hour")
                val minuteVal = obj.optInt("minute", -1)
                val secondVal = obj.optInt("second", -1)
                newRecords.add(HealthRecord(
                    date = LocalDate.parse(obj.getString("date")),
                    hour = if (hourVal == -1) null else hourVal,
                    minute = if (minuteVal == -1) null else minuteVal,
                    second = if (secondVal == -1) null else secondVal,
                    type = obj.getString("type"),
                    value = obj.getDouble("value").toFloat(),
                    itemName = obj.optString("itemName").let { if (it.isEmpty()) null else it },
                    unit = obj.optString("unit").let { if (it.isEmpty()) null else it },
                    isBinge = obj.optBoolean("isBinge", false) // 불러오기
                ))
            }

            _records.clear()
            _records.addAll(newRecords)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    // 자동 연동 데이터 (수면, 걸음수 등)는 기존 값을 지우고 하루 전체의 갱신값으로 덮어씌웁니다.
    fun updateAutoRecord(date: LocalDate, type: String, value: Float) {
        loadFromStorage() // 최신 데이터 동기화
        _records.removeAll { it.date == date && it.type == type }
        _records.add(HealthRecord(date, null, null, null, type, value))
        saveToStorage()
    }

    // 수동 입력 데이터 (알코올, 흡연 등)는 기존 총량과의 차이(Delta)를 구해 특정 시간에 누적합니다.
    fun updateManualRecord(date: LocalDate, hour: Int?, minute: Int?, second: Int?, type: String, newValue: Float, itemName: String? = null, unit: String? = null) {
        loadFromStorage() // 최신 데이터 동기화
        val currentTotal = getTodayValue(type)
        val delta = newValue - currentTotal
        if (delta > 0f) {
            // 새 레코드를 임시 추가하여 패널티 상태를 정확히 계산
            val tempRecord = HealthRecord(date, hour, minute, second, type, delta, itemName, unit, false)
            _records.add(tempRecord)
            
            // PenaltyDetails를 통해 방금 추가된 레코드의 isOverThreshold 값을 확인
            val details = getPenaltyDetails(type)
            val isBinge = details.lastOrNull()?.isOverThreshold ?: false
            
            // _records에서 방금 추가한 임시 레코드를 삭제하고 isBinge 정보가 포함된 진짜 레코드 추가
            _records.removeAt(_records.size - 1)
            _records.add(tempRecord.copy(isBinge = isBinge))

            saveToStorage()
        }
    }

    fun getValueForDate(type: String, date: LocalDate): Float {
        return _records.filter { it.date == date && it.type == type }.sumOf { it.value.toDouble() }.toFloat()
    }

    fun getTodayValue(type: String): Float {
        return getValueForDate(type, LocalDate.now())
    }

    fun getChartData(type: String, period: String): List<Pair<String, Float>> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("M/d")

        // 데이터가 있는 가장 오래된 날짜 확인
        val oldestRecordDate = _records.filter { it.type == type }.minByOrNull { it.date }?.date ?: today

        return if (period == "단위(일)") {
            (4 downTo 0).mapNotNull { i ->
                val date = today.minusDays(i.toLong())
                // 데이터가 전혀 없는 날(설치 전)은 차트에서 제외 (단, 오늘부터 과거 5일 중 데이터가 있는 시점까지만)
                if (date.isBefore(oldestRecordDate)) return@mapNotNull null
                
                val value = getValueForDate(type, date)
                Pair(date.format(formatter), value)
            }
        } else {
            (4 downTo 0).mapNotNull { i ->
                val weekStart = today.minusWeeks(i.toLong()).with(DayOfWeek.MONDAY)
                // 해당 주차에 데이터가 하나도 없으면 제외
                val hasDataInWeek = _records.any { it.type == type && !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) }
                if (!hasDataInWeek && weekStart.isBefore(oldestRecordDate.with(DayOfWeek.MONDAY))) return@mapNotNull null

                var weeklySum = 0f
                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date <= today) {
                        weeklySum += getValueForDate(type, date)
                    }
                }
                Pair("${weekStart.format(formatter)}~", weeklySum)
            }
        }
    }

    // 걸음수를 제외한 각 건강 지표를 통해 0~100점의 종합 점수를 계산합니다.
    fun getHealthScore(): Int {
        var score = 100f
        
        // 자동 측정 항목 및 수동 입력 항목 감점
        val activityPenalty = getTotalCurrentPenalty("활동시간")
        val alcoholPenalty = getTotalCurrentPenalty("알코올")
        val smokePenalty = getTotalCurrentPenalty("흡연")
        val caffeinePenalty = getTotalCurrentPenalty("카페인")
        val sleepPenalty = getTotalCurrentPenalty("수면")
        val screenPenalty = getTotalCurrentPenalty("스크린 타임")

        // score += penalty 인 이유는 각 Penalty 메서드가 음수(감점)를 반환하기 때문입니다.
        score += (activityPenalty + alcoholPenalty + smokePenalty + caffeinePenalty + sleepPenalty + screenPenalty)
        
        return Math.round(score).coerceIn(0, 100)
    }

    // 가장 감점이 큰 요소를 찾아 맞춤형 피드백을 제공합니다. (세부 기준 및 시나리오 반영)
    fun getHealthFeedback(): String {
        val now = LocalDate.now()
        val isMale = gender == "Male"

        // 1. 수면: 최근 3일 가중치 합산 (H = H1*0.5 + H2*0.3 + H3*0.2)
        val h1 = getValueForDate("수면", now)
        val h2 = getValueForDate("수면", now.minusDays(1))
        val h3 = getValueForDate("수면", now.minusDays(2))
        val weightedSleep = h1 * 0.5f + h2 * 0.3f + h3 * 0.2f

        // 2. 알코올: 최근 3일 합산 (기준: 남 120g, 여 60g)
        val alcoholSum = (0..2).sumOf { getValueForDate("알코올", now.minusDays(it.toLong())).toDouble() }.toFloat()
        val alcoholThreshold = if (isMale) 120f else 60f

        // 3. 흡연: 최근 7일 합산 (기준: 남 91개비, 여 49개비)
        val smokingSum = (0..6).sumOf { getValueForDate("흡연", now.minusDays(it.toLong())).toDouble() }.toFloat()
        val smokingThreshold = if (isMale) 91f else 49f

        // 4. 카페인: 최근 2일 합산 (기준: 남 800mg, 여 600mg)
        val caffeineSum = (0..1).sumOf { getValueForDate("카페인", now.minusDays(it.toLong())).toDouble() }.toFloat()
        val caffeineThreshold = if (isMale) 800f else 600f

        // 5. 스크린 타임 & 활동하기: 최근 1일 (사용자 설정 목표 G 기준)
        val screenTime = getTodayValue("스크린 타임")
        val activityTime = getTodayValue("활동시간")

        // 6. 폭주 패널티 이력 확인 (최근 24시간 이내)
        val bingeCheckTime = LocalDateTime.now().minusDays(1)
        fun hasRecentBinge(type: String) = getPenaltyDetails(type).any { it.isOverThreshold && it.dateTime.isAfter(bingeCheckTime) }

        val factors = listOf(
            HealthFactor(HealthFactorType.SLEEP, weightedSleep, 8.0f, hasRecentBinge("수면")),
            HealthFactor(HealthFactorType.ACTIVITY, activityTime, activityTarget, hasRecentBinge("활동시간")),
            HealthFactor(HealthFactorType.SCREEN_TIME, screenTime, screenTimeTarget, hasRecentBinge("스크린 타임")),
            HealthFactor(HealthFactorType.ALCOHOL, alcoholSum, alcoholThreshold, hasRecentBinge("알코올")),
            HealthFactor(HealthFactorType.SMOKING, smokingSum, smokingThreshold, hasRecentBinge("흡연")),
            HealthFactor(HealthFactorType.CAFFEINE, caffeineSum, caffeineThreshold, hasRecentBinge("카페인"))
        )

        return HealthNotificationEngine().generateIntegratedNotification(factors) ?: "건강한 하루를 보내고 계시네요! 지금처럼만 유지하세요."
    }

    /** 인과관계 기반 통합 알림 엔진 (최종 버전) **/
    private inner class HealthNotificationEngine {
        
        fun generateIntegratedNotification(factors: List<HealthFactor>): String? {
            val candidates = factors.filter { it.status == HealthStatus.DANGER || it.status == HealthStatus.CAUTION }
            if (candidates.isEmpty()) return null

            // 우선순위 정렬: 위험 > 주의 > 영향력 > 초과비율 > 최신성
            val sorted = candidates.sortedWith(
                compareByDescending<HealthFactor> { it.status.priority }
                    .thenByDescending { it.type.impactWeight }
                    .thenByDescending { it.ratioValue }
                    .thenByDescending { it.lastUpdated }
            )

            val count = sorted.size
            val main = sorted.first()

            return when {
                count >= 4 -> buildEmergencyMessage(sorted)
                count == 3 -> buildTripleMessage(sorted)
                count == 2 -> buildDoubleMessage(main, sorted[1])
                else -> buildSingleMessage(main)
            }
        }

        // 케이스 1: 2개 요소 누적 (인과관계 강조형)
        private fun buildDoubleMessage(main: HealthFactor, partner: HealthFactor): String {
            val pair = setOf(main.type, partner.type)
            val problem: String
            val action: String
            
            when {
                pair.contains(HealthFactorType.SLEEP) && pair.contains(HealthFactorType.ALCOHOL) -> {
                    problem = "수면 부족 상태에서 음주가 겹쳐 숙취 해소가 몹시 느려지고 있어요. 🥺 알코올이 분해되면 교감신경이 활성화되어 수면의 질이 나빠집니다."
                    action = "오늘은 술자리 대신 물을 충분히 마시고 푹 쉬어보세요. 간에 휴식을 주면 내일 컨디션이 빠르게 돌아옵니다!"
                }
                pair.contains(HealthFactorType.SLEEP) && pair.contains(HealthFactorType.CAFFEINE) -> {
                    problem = "카페인과 수면이 위험 상태입니다! ☕ 몸에 남은 과도한 카페인이 정신을 각성시켜 깊은 수면을 방해하고 있어요."
                    action = "오늘 밤엔 커피 대신 디카페인이나 따뜻한 차를 한 잔 마시고 일찍 누워볼까요? 잠만 제대로 푹 자도 내일 아침 피로감이 확 사라질 거예요."
                }
                pair.contains(HealthFactorType.SLEEP) && pair.contains(HealthFactorType.SCREEN_TIME) -> {
                    problem = "늦은 시간까지 이어진 스크린타임이 수면 부족의 직격탄이 되고 있어요. 📱 취침 전 밝은 화면은 수면 호르몬을 막습니다."
                    action = "오늘 밤은 자기 30분 전부터 스마트폰을 엎어두는 건 어떨까요? 디지털 디톡스가 꿀잠을 가져다줄 거예요."
                }
                pair.contains(HealthFactorType.ACTIVITY) && pair.contains(HealthFactorType.SCREEN_TIME) -> {
                    problem = "어제 너무 오래 앉아 화면만 보셨네요! 💻 활동량은 뚝 떨어지고 스크린타임은 한도를 초과했습니다."
                    action = "지금 당장 10분만 밖을 걸으며 굳은 몸과 눈의 피로를 풀어주세요. 잠깐의 산책이 남은 하루의 집중력을 높여줍니다."
                }
                pair.contains(HealthFactorType.ALCOHOL) && pair.contains(HealthFactorType.SMOKING) -> {
                    problem = "잦은 술자리와 흡연으로 몸의 기초 체력이 급격히 깎이고 있어요. 🚬 알코올과 니코틴이 동시에 들어오면 혈관에 큰 무리가 갑니다."
                    action = "오늘은 술과 담배 모두 딱 한 번만 참아보세요. 작은 절제가 내일의 가벼운 몸을 만듭니다."
                }
                pair.contains(HealthFactorType.SLEEP) && pair.contains(HealthFactorType.SMOKING) -> {
                    problem = "수면 부족 상태에서 흡연까지 겹치면 몸이 회복할 시간을 거의 못 찾고 있어요. 🚬 니코틴은 각성을 높여 잠드는 걸 더 어렵게 만들고, 잠이 부족할수록 담배 생각도 더 자주 날 수 있어요."
                    action = "오늘은 자기 전 흡연을 한 번만 건너뛰고, 대신 물을 마시며 몸을 천천히 진정시켜보세요. 잠만 조금 더 확보해도 내일의 피로감과 흡연 욕구가 함께 줄어들 수 있어요."
                }
                pair.contains(HealthFactorType.SLEEP) && pair.contains(HealthFactorType.ACTIVITY) -> {
                    problem = "수면이 부족한 상태가 계속되면 몸이 움직일 에너지까지 같이 떨어져요. 😴 반대로 활동량이 너무 적으면 밤에도 몸이 제대로 피로해지지 않아 수면의 질이 더 나빠질 수 있어요."
                    action = "오늘은 무리한 운동보다 10분 정도 가볍게 걸으며 몸의 리듬을 다시 깨워보세요. 잠깐의 움직임이 꿀잠과 내일의 컨디션을 동시에 도와줄 거예요."
                }
                else -> {
                    problem = "현재 ${main.type.displayName} 상태가 가장 위험해요! 🚨 여기에 ${partner.type.displayName}까지 더해져 몸의 회복을 방해하고 있습니다."
                    action = "오늘은 다른 것보다 ${main.type.displayName}을(를) 조절하는 데 가장 먼저 신경 써보세요. 하나씩 해결하다 보면 컨디션은 금방 돌아옵니다."
                }
            }
            return "$problem\n\n$action\n\n${getMotivation(main.status)}"
        }

        // 케이스 2: 3개 요소 누적 (핵심 1개 + 방해 2개)
        private fun buildTripleMessage(sorted: List<HealthFactor>): String {
            val main = sorted[0]
            val p1 = sorted[1]
            val p2 = sorted[2]
            
            val problem = when (main.type) {
                HealthFactorType.SLEEP -> "현재 수면 상태가 가장 위험해요! 🚨 게다가 최근 ${p1.type.displayName}와 ${p2.type.displayName}이 몸의 회복을 심각하게 방해하고 있습니다."
                HealthFactorType.CAFFEINE -> "현재 카페인 섭취량이 매우 위험 수준이에요! 🧠 여기에 부족한 ${p1.type.displayName}와 과도한 ${p2.type.displayName}이 더해져 뇌가 쉴 틈이 없습니다."
                HealthFactorType.ALCOHOL -> "알코올 섭취량이 최고 위험 단계예요! 🍻 ${p1.type.displayName}은 부족하고 ${p2.type.displayName}마저 흔들려 피로가 쌓이고 있습니다."
                else -> "${main.type.displayName} 수치가 매우 심각합니다! ${p1.type.displayName}와 ${p2.type.displayName} 또한 건강 회복을 가로막고 있어요."
            }
            
            val action = "오늘은 천천히 '${main.type.displayName} 조절'에만 집중해보세요. 그렇게 차근차근 ${p1.type.displayName}와(과) ${p2.type.displayName}도 점차 개선되는 나를 발견할 수 있을 거에요."
            return "$problem\n\n$action\n\n${getMotivation(main.status)}"
        }

        // 케이스 3: 4개 이상 요소 누적 (긴급 진화형)
        private fun buildEmergencyMessage(sorted: List<HealthFactor>): String {
            val main = sorted[0]
            val p2 = sorted[1]
            val p3 = sorted[2]
            
            val problem = "${main.type.displayName} 상태에 빨간불이 켜졌어요! ⚠️ 거기에 ${p2.type.displayName}, ${p3.type.displayName} 등 여러 문제가 겹쳐 전반적으로 몸이 많이 지쳐있습니다."
            val action = "한 번에 다 고치기 어렵다면, 오늘은 딱 하나! ${main.type.displayName} 관련 습관부터 실천해 보세요. 작은 행동 하나가 무너진 리듬을 되돌리는 시작이 됩니다."
            
            return "$problem\n\n$action\n\n건강은 한 걸음부터입니다. 오늘 가장 중요한 하나만이라도 꼭 지켜내 봐요!"
        }

        private fun buildSingleMessage(main: HealthFactor): String {
            val problems = when (main.type) {
                HealthFactorType.SLEEP -> listOf(
                    "😵 최근 수면이 부족해 뇌가 절전 모드에 들어가려 하고 있어요.",
                    "🛌 침대는 매일 출석했는데, 당신은 조금 늦게 도착한 것 같네요.",
                    "🌙 수면 잔고가 부족합니다. 오늘의 피곤함은 어제의 수면 부족이 보낸 청구서일지도 몰라요.",
                    "⚠️ 몸이 충전기 연결을 요청하고 있습니다. 배터리가 빨간색에 가까워지고 있어요."
                )
                HealthFactorType.ACTIVITY -> listOf(
                    "🪑 의자가 오늘 당신과 너무 친해졌어요. 몸이 슬슬 산책을 요청하고 있습니다.",
                    "🚶 오늘의 걸음 수가 아직 워밍업 단계에 머물러 있어요.",
                    "😴 몸이 \"혹시 오늘 휴무인가요?\"라고 묻고 있습니다. 활동량이 조금 부족한 상태예요.",
                    "🌱 근육들이 오랜만에 출근 연락을 기다리고 있어요. 오늘은 움직임이 조금 적었네요."
                )
                HealthFactorType.SMOKING -> listOf(
                    "🚬 미래의 폐가 조용히 항의서를 작성하고 있습니다. 최근 흡연량이 조금 많아졌어요.",
                    "😮‍💨 폐가 오늘도 공기 정화 야근 중입니다. 잠시 휴식이 필요해 보여요.",
                    "🌫️ 몸속 공기청정기가 평소보다 바쁘게 돌아가고 있습니다.",
                    "🚨 폐와 혈관이 \"업무량 조정 요청서\"를 제출했습니다. 최근 흡연이 조금 과한 상태예요."
                )
                HealthFactorType.ALCOHOL -> listOf(
                    "🍺 간이 오늘도 야근 신청서를 제출했습니다. 최근 음주량이 조금 많았던 것 같아요.",
                    "😵 어제의 술자리가 아직 몸속 단체 채팅방을 떠나지 않고 있습니다.",
                    "🍻 술은 이미 집에 갔는데, 몸은 아직 뒤풀이 중인 것 같네요.",
                    "🚨 간과 위장이 \"잠깐만 쉬자\"는 공동 성명을 발표했습니다."
                )
                HealthFactorType.CAFFEINE -> listOf(
                    "☕ 커피가 오늘도 열심히 일하고 있습니다. 문제는 이제 몸보다 커피가 더 바빠 보인다는 점이에요.",
                    "⚡ 카페인이 뇌에 \"아직 안 자도 된다\"는 잘못된 소문을 퍼뜨리고 있습니다.",
                    "😵 최근 카페인 섭취가 늘어나면서 몸이 자연 충전보다 외부 배터리에 의존하고 있어요.",
                    "🚨 수면 담당 부서에서 민원이 접수되었습니다. 카페인이 아직 퇴근하지 못한 상태예요."
                )
                HealthFactorType.SCREEN_TIME -> listOf(
                    "📱 휴대폰이 오늘 당신과 거의 한 몸이 된 것 같습니다. 화면을 바라본 시간이 꽤 길어졌어요.",
                    "👀 눈이 \"잠깐만 쉬자\"는 신호를 보내고 있습니다. 오늘 화면과 함께한 시간이 많았네요.",
                    "😵 스마트폰이 오늘의 베스트 프렌드가 되어버렸습니다. 몸은 잠시 휴식을 원하고 있어요.",
                    "🚨 스크린 사용 시간이 목표치를 넘어섰습니다. 눈과 목이 슬슬 파업을 준비하는 중이에요."
                )
            }

            val actions = when (main.type) {
                HealthFactorType.SLEEP -> listOf(
                    "💡 오늘은 평소보다 30분만 일찍 누워보세요. 작은 변화가 큰 차이를 만듭니다.",
                    "📱 잠들기 1시간 전에는 휴대폰과 잠시 거리 두기를 해보세요.",
                    "🌿 자기 전 따뜻한 물이나 차 한 잔으로 몸과 마음을 천천히 진정시켜 보세요.",
                    "⏰ 내일 기상 시간을 유지하고, 오늘은 조금 더 일찍 잠자리에 들어보세요."
                )
                HealthFactorType.ACTIVITY -> listOf(
                    "🚶 지금 10분만 걸어보세요. 생각보다 많은 건강 포인트를 얻을 수 있습니다.",
                    "🏢 엘리베이터 대신 계단 한 번 이용해 보세요. 작은 움직임도 충분히 의미 있습니다.",
                    "💧 물 한 잔 마시러 가는 김에 잠깐 자리에서 일어나 몸을 풀어보세요.",
                    "🏃 가벼운 스트레칭이나 산책으로 몸에게 \"오늘도 함께하고 있다\"는 신호를 보내주세요."
                )
                HealthFactorType.SMOKING -> listOf(
                    "💧 담배 생각이 날 때 물 한 잔을 먼저 마셔보세요. 의외로 도움이 될 수 있습니다.",
                    "🚶 이번 한 번은 담배 대신 5분 정도 가볍게 걸어보는 건 어떨까요?",
                    "📉 오늘은 평소보다 딱 한 개비만 줄여보세요. 작은 변화도 충분히 의미 있습니다.",
                    "🌿 흡연 욕구가 생긴다면 잠시 심호흡을 하며 10분만 미뤄보세요."
                )
                HealthFactorType.ALCOHOL -> listOf(
                    "💧 지금 물 한 잔 마셔보세요. 몸이 생각보다 많이 반길 거예요.",
                    "🚶 10분 정도 가볍게 산책하며 몸의 회복 시간을 만들어 주세요.",
                    "🍎 다음 술자리가 있다면 안주와 물을 함께 챙겨보세요. 간이 조금 덜 힘들어집니다.",
                    "🌙 오늘은 술 대신 일찍 잠들어 보는 건 어떨까요? 회복 속도가 훨씬 빨라집니다."
                )
                HealthFactorType.CAFFEINE -> listOf(
                    "💧 다음 커피 대신 물 한 잔을 먼저 마셔보세요. 생각보다 피로가 줄어들 수 있습니다.",
                    "🌙 오후 늦은 시간이라면 카페인 대신 잠깐의 휴식을 선택해 보세요.",
                    "☕ 오늘은 평소보다 한 잔만 덜 마셔보세요. 몸이 금방 변화를 느낄 수 있습니다.",
                    "🚶 졸릴 때는 커피를 찾기 전에 5분 정도 걸으며 몸을 깨워보세요."
                )
                HealthFactorType.SCREEN_TIME -> listOf(
                    "🌳 20초 동안 창밖이나 먼 곳을 바라보며 눈을 쉬게 해주세요.",
                    "🚶 휴대폰을 내려놓고 5분 정도 가볍게 걸어보세요. 몸과 눈이 함께 회복됩니다.",
                    "💧 물 한 잔 마시면서 잠시 화면에서 눈을 떼어보세요.",
                    "📵 지금부터 10분만 휴대폰 없이 시간을 보내보는 건 어떨까요?"
                )
            }

            val motivations = when (main.type) {
                HealthFactorType.SLEEP -> listOf(
                    "🌞 오늘의 숙면은 내일의 집중력과 기분을 선물해 줍니다.",
                    "🚀 충분한 수면은 가장 강력한 무료 성능 업그레이드입니다.",
                    "💪 운동도, 공부도, 다이어트도 결국 잠을 잘 자야 더 효과적이에요.",
                    "🌱 오늘 한 번의 숙면이 쌓여 더 건강한 내일을 만듭니다."
                )
                HealthFactorType.ACTIVITY -> listOf(
                    "💪 건강은 거창한 운동보다 꾸준한 움직임에서 시작됩니다.",
                    "🌟 지금의 10분이 미래의 체력 1시간이 되어 돌아올 수 있어요.",
                    "🚀 몸은 생각보다 금방 반응합니다. 지금 한 걸음이 내일의 컨디션을 바꿀 수 있어요.",
                    "🌱 오늘의 작은 움직임이 건강한 습관의 씨앗이 됩니다."
                )
                HealthFactorType.SMOKING -> listOf(
                    "🌱 한 개비 줄이는 것도 건강에는 분명한 플러스입니다.",
                    "💪 몸은 생각보다 빠르게 회복합니다. 오늘의 작은 실천이 내일의 변화를 만듭니다.",
                    "🚀 완벽한 금연보다 꾸준한 감소가 더 현실적이고 오래갑니다.",
                    "🌞 미래의 폐는 오늘의 선택을 기억합니다. 지금의 한 번이 생각보다 큰 차이를 만들 수 있어요."
                )
                HealthFactorType.ALCOHOL -> listOf(
                    "🌞 오늘 하루만 쉬어가도 내일 아침 컨디션은 눈에 띄게 달라질 수 있어요.",
                    "💪 간은 생각보다 회복력이 뛰어납니다. 회복할 시간만 주면 돼요.",
                    "🚀 한 번 덜 마시는 선택이 다음 날의 집중력과 에너지를 지켜줍니다.",
                    "🌱 건강은 거창한 결심보다 작은 절제가 더 오래갑니다."
                )
                HealthFactorType.CAFFEINE -> listOf(
                    "🌱 카페인을 조금 줄이면 내일의 수면이 더 깊어질 수 있어요.",
                    "💪 진짜 에너지는 커피가 아니라 충분한 휴식에서 나옵니다.",
                    "🚀 오늘 한 잔 덜 마시는 선택이 내일의 집중력을 지켜줄 수 있어요.",
                    "🌞 몸은 생각보다 금방 균형을 되찾습니다. 작은 변화도 충분히 가치 있어요."
                )
                HealthFactorType.SCREEN_TIME -> listOf(
                    "🌱 잠깐의 휴식이 오히려 집중력을 더 오래 유지하게 도와줍니다.",
                    "👀 눈도 근육입니다. 잠시 쉬어주면 더 선명하게 세상을 볼 수 있어요.",
                    "🚀 화면을 잠시 내려놓는 것이 생산성을 올리는 가장 쉬운 방법일 수 있습니다.",
                    "🌞 휴대폰 밖에도 생각보다 즐거운 것들이 많이 기다리고 있어요."
                )
            }

            return "${problems.random()}\n\n${actions.random()}\n\n${motivations.random()}"
        }

        private fun getMotivation(status: HealthStatus): String = when (status) {
            HealthStatus.DANGER -> listOf(
                "이대로라면 내일 첫 수업은 '자체휴강' 확정입니다. 지금 바로 움직여요!",
                "몸이 보내는 경고를 무시하지 마세요. 지금의 휴식이 내일의 당신을 구합니다! 🚨",
                "배터리가 0%가 되기 전에 충전하는 것이 필연적이에요. 오늘만큼은 자신을 최우선으로 돌봐주세요."
            ).random()
            else -> listOf(
                "조금만 신경 쓰면 다시 쌩쌩한 컨디션으로 돌아갈 수 있어요. 화이팅!",
                "작은 습관 하나만 바꿔도 몸이 훨씬 가벼워질 거예요. 당신의 건강을 응원합니다! ✨",
                "완벽하지 않아도 괜찮아요. 지금 할 수 있는 작은 것부터 하나씩 실천해 봐요."
            ).random()
        }
    }

    // --- 추가된 메서드들 ---

    fun saveSelection(type: String, name: String) {
        prefs?.edit()?.putString("selection_$type", name)?.apply()
    }

    fun saveLocalPartyName(partyId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            // 공백만 입력 시 로컬 별명 삭제 -> 기본 이름으로 복구
            _localPartyNames.remove(partyId)
            prefs?.edit()?.remove("party_name_$partyId")?.apply()
        } else {
            _localPartyNames[partyId] = trimmedName
            prefs?.edit()?.putString("party_name_$partyId", trimmedName)?.apply()
        }
    }

    fun getLocalPartyName(partyId: String, defaultName: String): String {
        return _localPartyNames[partyId] ?: prefs?.getString("party_name_$partyId", defaultName) ?: defaultName
    }

    fun saveProfile() {
        prefs?.edit()?.apply {
            putString("userName", userName)
            putString("bio", bio)
            putBoolean("isPublic", isPublic)
            putString("gender", gender)
            putBoolean("isOnboardingCompleted", isOnboardingCompleted)
            putFloat("activityTarget", activityTarget)
            putFloat("alcoholTarget", alcoholTarget)
            putFloat("smokingTarget", smokingTarget)
            putFloat("caffeineTarget", caffeineTarget)
            putFloat("sleepTarget", sleepTarget)
            putFloat("screenTimeTarget", screenTimeTarget)
            commit()
        }
    }

    fun loadSelections() {
        // UID 결정 로직
        userId = Firebase.auth.currentUser?.uid 
            ?: context?.let { Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID) }
            ?: "guest_user"

        userName = prefs?.getString("userName", "") ?: ""
        bio = prefs?.getString("bio", "") ?: ""
        isPublic = prefs?.getBoolean("isPublic", true) ?: true
        val alcoholName = prefs?.getString("selection_알코올", "소주") ?: "소주"
        alcoholTypes.find { it.name == alcoholName }?.let { selectedAlcoholType = it }
        
        val caffeineName = prefs?.getString("selection_카페인", "아메리카노") ?: "아메리카노"
        caffeineTypes.find { it.name == caffeineName }?.let { selectedCaffeineType = it }

        gender = prefs?.getString("gender", "Male") ?: "Male"
        isOnboardingCompleted = prefs?.getBoolean("isOnboardingCompleted", false) ?: false
        
        _activityTarget = prefs?.getFloat("activityTarget", 2.0f) ?: 2.0f
        alcoholTarget = prefs?.getFloat("alcoholTarget", 24f) ?: 24f
        smokingTarget = prefs?.getFloat("smokingTarget", 26f) ?: 26f
        caffeineTarget = prefs?.getFloat("caffeineTarget", 30f) ?: 30f
        sleepTarget = prefs?.getFloat("sleepTarget", 8f) ?: 8f
        screenTimeTarget = prefs?.getFloat("screenTimeTarget", 6f) ?: 6f
    }

    fun getTotalCurrentPenalty(type: String): Float {
        if (type == "활동시간") {
            val h = getTodayValue("활동시간") * 60f // 현재 활동 시간 (분)
            val target = activityTarget * 60f // 목표 활동 시간 (분)
            // 공식: -3.0 * (ActiveGoals(분) - H(분)) / 60
            // 즉, 부족한 시간당 -3점 감점 (분당 -0.05점)
            // 예: 목표 180분(3시간), 현재 60분(1시간) -> -3.0 * (120 / 60) = -6.0점
            return -3.0f * Math.max(0f, target - h) / 60f
        }
        if (type == "수면") {
            val now = LocalDate.now()
            val s1 = calculateSleepScore(getValueForDate("수면", now))
            val s2 = calculateSleepScore(getValueForDate("수면", now.minusDays(1)))
            val s3 = calculateSleepScore(getValueForDate("수면", now.minusDays(2)))
            return s1 * 0.5f + s2 * 0.3f + s3 * 0.2f
        }
        if (type == "스크린 타임") {
            val screen = getTodayValue("스크린 타임")
            return if (screen > screenTimeTarget) -(screen - screenTimeTarget) else 0f
        }
        return getPenaltyDetails(type).sumOf { it.currentPenalty.toDouble() }.toFloat()
    }

    private fun calculateSleepScore(hours: Float): Float {
        val h = hours.coerceAtMost(7f)
        return (-10f * Math.pow((h - 7.0), 2.0) ).toFloat()
    }

    fun getPenaltyDetails(type: String): List<PenaltyDetail> {
        if (type == "활동시간") {
            val h = getTodayValue("활동시간")
            val penalty = getTotalCurrentPenalty("활동시간")
            return listOf(
                PenaltyDetail(
                    dateTime = LocalDate.now().atTime(0, 0),
                    originalValue = h,
                    isOverThreshold = h < activityTarget,
                    currentPenalty = penalty
                )
            )
        }
        if (type == "수면") {
            val now = LocalDate.now()
            // 최근 3일의 수면에 대해서만 가중치가 적용된 실제 반영 점수를 로그로 보여줍니다.
            val weights = listOf(0.5f, 0.3f, 0.2f)
            return (0..2).map { i ->
                val targetDate = now.minusDays(i.toLong())
                val hours = getValueForDate("수면", targetDate)
                val baseScore = calculateSleepScore(hours)
                val weightedPenalty = baseScore * weights[i]
                
                PenaltyDetail(
                    dateTime = targetDate.atTime(0, 0),
                    originalValue = hours,
                    isOverThreshold = hours < 7f,
                    currentPenalty = weightedPenalty
                )
            }
        }

        if (type == "스크린 타임") {
            val screen = getTodayValue("스크린 타임")
            val penalty = if (screen > screenTimeTarget) -(screen - screenTimeTarget) else 0f
            return listOf(
                PenaltyDetail(
                    dateTime = LocalDate.now().atTime(0, 0),
                    originalValue = screen,
                    isOverThreshold = screen > screenTimeTarget,
                    currentPenalty = penalty
                )
            )
        }

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
                "알코올" -> if (gender == "Female") 20f else 40f
                "카페인" -> if (gender == "Female") 300f else 400f
                "흡연" -> if (gender == "Female") 7f else 13f
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
                currentPenalty = currentPenalty,
                itemName = record.itemName,
                unit = record.unit,
                minute = record.minute
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
            val ctx = context?.applicationContext
            return instance ?: synchronized(this) {
                instance ?: HealthState(ctx).also { 
                    instance = it 
                }
            }
        }
    }
}
