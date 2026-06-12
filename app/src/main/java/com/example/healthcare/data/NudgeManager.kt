package com.example.healthcare.data

import android.content.Context
import android.util.Log
import java.time.LocalDateTime

/**
 * 콕 찌르기 메시지 및 발송 제한 관리 클래스
 */
object NudgeManager {
    
    // 기본 메시지 맵 (항목별)
    private val defaultMessages = mapOf(
        "알코올" to listOf("술은 적당히! 건강을 생각해서 조금만 줄여볼까요? 🍺", "오늘 페이스가 좀 빠르신 것 같아요! 물 한 잔 어떠세요? 💧"),
        "카페인" to listOf("카페인 섭취가 많아요! 숙면을 위해 이제 그만~ ☕", "심장이 두근거리진 않나요? 카페인 대신 시원한 물 한 잔! 🥤"),
        "흡연" to listOf("잠시 숨을 고르며 맑은 공기를 마셔보세요! 맑은 공기가 몸에 더 좋아요. 🚭", "오늘 조금 많이 피우신 것 같아요. 건강을 위해 하나만 참아볼까요? ✨")
    )

    // 로컬 제한 관리를 위한 관찰 가능한 맵 (Key: "receiverId_factor_day_hour")
    private val nudgeCounts = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    /**
     * 특정 파티원에 대해 특정 항목의 콕 찌르기가 가능한지 확인 (시간당 5회 제한)
     */
    fun canNudge(receiverId: String, factor: String): Boolean {
        val now = LocalDateTime.now()
        val key = "${receiverId}_${factor}_${now.dayOfYear}_${now.hour}"
        val count = nudgeCounts[key] ?: 0
        return count < 5
    }

    /**
     * 콕 찌르기 성공 시 횟수 기록
     */
    fun recordNudge(receiverId: String, factor: String) {
        val now = LocalDateTime.now()
        val key = "${receiverId}_${factor}_${now.dayOfYear}_${now.hour}"
        val currentCount = nudgeCounts[key] ?: 0
        nudgeCounts[key] = currentCount + 1
    }

    /**
     * 특정 항목에 대한 랜덤 메시지 반환
     */
    fun getMessage(factor: String): String {
        val messages = defaultMessages[factor] ?: listOf("건강을 위해 조금만 신경 써주세요! ❤️")
        return messages.random()
    }

    /**
     * 알림 제목 반환
     */
    fun getTitle(factor: String): String {
        return "콕 찌르기 ($factor)"
    }
}
