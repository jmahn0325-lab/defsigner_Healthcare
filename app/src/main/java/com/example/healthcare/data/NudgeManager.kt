package com.example.healthcare.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 콕 찌르기 메시지 관리 클래스
 * 향후 서버에서 메시지 목록을 가져오거나 확장하기 용이하도록 설계
 */
object NudgeManager {
    
    // 기본 메시지 맵 (항목별)
    private val defaultMessages = mapOf(
        "알코올" to listOf("술은 적당히! 건강을 생각해서 조금만 줄여볼까요? 🍺", "오늘 페이스가 좀 빠르신 것 같아요! 물 한 잔 어떠세요? 💧"),
        "카페인" to listOf("카페인 섭취가 많아요! 숙면을 위해 이제 그만~ ☕", "심장이 두근거리진 않나요? 카페인 대신 시원한 물 한 잔! 🥤"),
        "흡연" to listOf("잠시 숨을 고르며 맑은 공기를 마셔보세요! 🚭", "오늘 조금 많이 피우신 것 같아요. 건강을 위해 하나만 참아볼까요? ✨")
    )

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
