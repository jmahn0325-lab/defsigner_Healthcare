package com.example.healthcare.data

import com.google.firebase.Timestamp

/**
 * 파티(그룹) 정보
 */
data class Party(
    val partyId: String = "",
    val partyName: String = "",
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

/**
 * 리더보드용 유저 요약 정보
 */
data class UserScore(
    val uid: String = "",
    val displayName: String = "",
    val totalScore: Int = 0,
    val bio: String = "",
    val isPublic: Boolean = true,
    val fcmToken: String = ""
)

/**
 * 파티원 상세 정보 (프라이버시 필터링 후)
 */
data class MemberDetails(
    val uid: String,
    val displayName: String,
    val scores: Map<String, Float> // "알코올": 85.0f 등
)

/**
 * 콕 찌르기 (Nudge) 기록
 */
data class NudgeInteraction(
    val senderId: String = "",
    val receiverId: String = "",
    val targetFactor: String = "",
    val timestamp: Timestamp? = null
)

/**
 * 유저별 프라이버시 설정
 */
data class PrivacySettings(
    val alcoholVisible: Boolean = true,
    val caffeineVisible: Boolean = true,
    val smokingVisible: Boolean = true,
    val healthVisible: Boolean = true // 수면, 활동량 등
)
