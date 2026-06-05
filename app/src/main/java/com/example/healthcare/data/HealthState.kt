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
    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

    init {
        if (!loadFromStorage()) {
            saveToStorage()
        }
        loadSelections()
    }

    private fun getRecordsFile(): java.io.File? = context?.filesDir?.let { java.io.File(it, "health_records.json") }

    private fun saveToStorage() {
        val jsonArray = JSONArray()
        _records.forEach { record ->
            val obj = JSONObject()
            obj.put("date", record.date.toString())
            obj.put("hour", record.hour ?: -1)
            obj.put("type", record.type)
            obj.put("value", record.value.toDouble())
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
                newRecords.add(HealthRecord(
                    LocalDate.parse(obj.getString("date")),
                    if (hourVal == -1) null else hourVal,
                    obj.getString("type"),
                    obj.getDouble("value").toFloat()
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
        _records.add(HealthRecord(date, null, type, value))
        saveToStorage()
    }

    // 수동 입력 데이터 (알코올, 흡연 등)는 기존 총량과의 차이(Delta)를 구해 특정 시간에 누적합니다.
    fun updateManualRecord(date: LocalDate, hour: Int?, type: String, newValue: Float) {
        loadFromStorage() // 최신 데이터 동기화
        val currentTotal = getTodayValue(type)
        val delta = newValue - currentTotal
        if (delta > 0f) {
            _records.add(HealthRecord(date, hour, type, delta))
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
        
        return Math.round(score).toInt().coerceIn(0, 100)
    }

    // 가장 감점이 큰 요소를 찾아 맞춤형 피드백을 제공합니다.
    fun getHealthFeedback(): String {
        val penalties = mutableMapOf<String, Float>()
        
        val checkTypes = mutableListOf("활동시간", "카페인", "수면", "스크린 타임", "알코올", "흡연")

        checkTypes.forEach { 
            val penalty = getTotalCurrentPenalty(it)
            penalties[it] = if (penalty < 0) -penalty else 0f
        }

        val worstFactor = penalties.maxByOrNull { it.value }

        if (worstFactor == null || worstFactor.value == 0f) {
            return "건강한 하루를 보내고 계시네요! 지금처럼만 유지하세요."
        }

        return when (worstFactor.key) {
            "수면" -> "수면이 부족합니다. 뇌와 신체가 쉴 수 있도록 오늘 밤은 일찍 주무세요."
            "활동시간" -> "오래 앉아 계셨네요. 중간중간 일어나서 스트레칭을 해볼까요?"
            "스크린 타임" -> "전자기기 사용 시간이 깁니다. 눈과 뇌에 휴식을 주세요."
            "알코올" -> "음주량이 높습니다. 간이 회복할 수 있도록 오늘은 술을 참아보세요."
            "흡연" -> "흡연량이 많아 건강에 치명적입니다. 단기 금연부터 시도해보는 것은 어떨까요?"
            "카페인" -> "카페인 섭취가 높습니다. 커피 대신 물을 많이 마셔보세요."
            else -> "건강 수치에 주의가 필요합니다."
        }
    }

    // --- 추가된 메서드들 ---

    fun saveSelection(type: String, name: String) {
        prefs?.edit()?.putString("selection_$type", name)?.commit()
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
            val ctx = context?.applicationContext
            return instance ?: synchronized(this) {
                instance ?: HealthState(ctx).also { 
                    instance = it 
                }
            }
        }
    }
}
