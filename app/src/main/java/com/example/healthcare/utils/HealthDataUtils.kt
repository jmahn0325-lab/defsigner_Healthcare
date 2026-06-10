package com.example.healthcare.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

suspend fun getHistoricalActiveTime(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowInstant = Instant.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay(zoneId).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val actualEnd = if (endOfDay.isAfter(nowInstant)) nowInstant else endOfDay

            if (startOfDay.isAfter(actualEnd)) {
                map[date] = 0f
                continue
            }

            // 삼성 헬스 방식: 분 단위로 활동 여부를 체크하여 '활동적인 분(Active Minutes)'의 총합 계산
            val activeMinutesSet = mutableSetOf<Long>() 

            // 1. 명시적 운동 세션 (무조건 활동으로 간주)
            try {
                val exerciseRequest = ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, actualEnd)
                )
                client.readRecords(exerciseRequest).records.forEach { record ->
                    var temp = record.startTime.truncatedTo(ChronoUnit.MINUTES)
                    while (temp.isBefore(record.endTime)) {
                        activeMinutesSet.add(temp.toEpochMilli())
                        temp = temp.plus(1, ChronoUnit.MINUTES)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            // 2. 걸음 데이터를 통한 활동 판정
            try {
                val stepsRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, actualEnd)
                )
                val stepsResponse = client.readRecords(stepsRequest)
                
                // 모든 소스의 활동을 합집합으로 처리
                stepsResponse.records.forEach { record ->
                    val durationMillis = Duration.between(record.startTime, record.endTime).toMillis()
                    val durationMinutes = durationMillis / (1000.0 * 60.0)
                    
                    // 강도(Intensity) 계산: 분당 걸음 수 (Cadence)
                    val cadence = if (durationMinutes > 0) record.count / durationMinutes else record.count.toDouble()
                    
                    // 판정 기준: 삼성 헬스는 보통 분당 70~80보 이상을 '활동적'으로 판단함
                    if (cadence >= 70 || (record.count >= 30 && durationMinutes <= 1.5)) {
                        var temp = record.startTime.truncatedTo(ChronoUnit.MINUTES)
                        while (!temp.isAfter(record.endTime)) {
                            activeMinutesSet.add(temp.toEpochMilli())
                            temp = temp.plus(1, ChronoUnit.MINUTES)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            // 최종 활동 시간(시간 단위) = 활동적인 분들의 개수 / 60
            map[date] = activeMinutesSet.size.toFloat() / 60f
        }
    } catch (e: Exception) { 
        e.printStackTrace()
    }
    return map
}

suspend fun getHistoricalSleep(client: HealthConnectClient, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val nowInstant = Instant.now()

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val start = date.minusDays(1).atTime(12, 0).atZone(zoneId).toInstant()
            val end = date.atTime(12, 0).atZone(zoneId).toInstant()
            val actualEnd = if (end.isAfter(nowInstant)) nowInstant else end

            if (start.isAfter(actualEnd)) continue

            val response = client.readRecords(ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, actualEnd)
            ))

            val sorted = response.records.sortedBy { it.startTime }
            var totalMinutes = 0L
            var currentEnd = Instant.MIN

            for (record in sorted) {
                if (record.endTime.isAfter(currentEnd)) {
                    val effectiveStart = if (record.startTime.isAfter(currentEnd)) record.startTime else currentEnd
                    totalMinutes += ChronoUnit.MINUTES.between(effectiveStart, record.endTime)
                    currentEnd = record.endTime
                }
            }
            map[date] = totalMinutes / 60f
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

fun getHistoricalScreenTime(context: Context, days: Int): Map<LocalDate, Float> {
    val map = mutableMapOf<LocalDate, Float>()
    try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        // 런처 및 시스템 앱 필터링 준비
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val launchers = packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()

        val systemPackages = setOf(
            "com.android.systemui", "com.android.settings", "android",
            "com.samsung.android.app.aodservice", "com.samsung.android.app.cocktailbarservice",
            "com.google.android.permissioncontroller"
        )

        for (i in 0..days) {
            val date = today.minusDays(i.toLong())
            val startMilli = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMilli = if (i == 0) System.currentTimeMillis() else date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            // queryAndAggregateUsageStats는 중복 합산 위험이 큼. 
            // 삼성 디지털 웰빙처럼 정확한 스크린 '켜짐' 시간을 구하기 위해 이벤트 기반으로 변경
            val events = usageStatsManager.queryEvents(startMilli, endMilli)
            val event = android.app.usage.UsageEvents.Event()
            
            val appUsageMap = mutableMapOf<String, Long>() // 앱별 시작 시간 저장
            
            // 모든 앱의 사용 구간을 저장 (시작, 종료)
            val usageIntervals = mutableListOf<Pair<Long, Long>>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                
                // 필터링: 런처나 시스템 UI는 제외
                if (launchers.contains(pkg) || systemPackages.contains(pkg)) continue

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        appUsageMap[pkg] = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val startTime = appUsageMap.remove(pkg)
                        if (startTime != null && event.timeStamp > startTime) {
                            usageIntervals.add(startTime to event.timeStamp)
                        }
                    }
                }
            }

            // 겹치는 시간 구간 병합 (활동시간 로직과 동일하게 적용)
            // 여러 앱이 백그라운드에서 돌거나 동시에 활성화된 것으로 잡히는 경우 방지
            val mergedTotalMillis = if (usageIntervals.isNotEmpty()) {
                val sorted = usageIntervals.sortedBy { it.first }
                var currentStart = sorted[0].first
                var currentEnd = sorted[0].second
                var total = 0L

                for (idx in 1 until sorted.size) {
                    val nextStart = sorted[idx].first
                    val nextEnd = sorted[idx].second

                    if (nextStart <= currentEnd) {
                        currentEnd = maxOf(currentEnd, nextEnd)
                    } else {
                        total += (currentEnd - currentStart)
                        currentStart = nextStart
                        currentEnd = nextEnd
                    }
                }
                total += (currentEnd - currentStart)
                total
            } else 0L

            map[date] = mergedTotalMillis.toFloat() / (1000f * 60f * 60f)
        }
    } catch (e: Exception) { e.printStackTrace() }
    return map
}

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}