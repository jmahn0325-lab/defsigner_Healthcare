package com.example.healthcare.data

/**
 * 콕 찌르기 메시지 관리를 위한 Sealed Class 구조
 * 향후 새로운 타입이나 멘트 목록이 추가되어도 기존 로직 수정 없이 확장 가능합니다.
 */
sealed class NudgeType(val factorName: String) {
    object Alcohol : NudgeType("알코올")
    object Caffeine : NudgeType("카페인")
    object Smoking : NudgeType("흡연")
    class Custom(name: String) : NudgeType(name)

    companion object {
        fun fromString(factor: String): NudgeType {
            return when (factor) {
                "알코올" -> Alcohol
                "카페인" -> Caffeine
                "흡연" -> Smoking
                else -> Custom(factor)
            }
        }
    }
}

object NudgeMessageProvider {
    
    // 멘트 목록 (향후 원격 구성(Remote Config)이나 DB에서 가져오도록 확장 가능)
    private val messages = mapOf(
        NudgeType.Alcohol to listOf(
            "술은 적당히! 건강을 생각해서 조금만 줄여볼까요? 🍺",
            "오늘 페이스가 좀 빠르신 것 같아요! 물 한 잔 어떠세요? 💧"
        ),
        NudgeType.Caffeine to listOf(
            "카페인 섭취가 많아요! 숙면을 위해 이제 그만~ ☕",
            "심장이 두근거리진 않나요? 카페인 대신 시원한 물 한 잔! 🥤"
        ),
        NudgeType.Smoking to listOf(
            "잠시 숨을 고르며 맑은 공기를 마셔보세요! 🚭",
            "오늘 조금 많이 피우신 것 같아요. 건강을 위해 하나만 참아볼까요? ✨"
        )
    )

    fun getTitle(senderName: String, type: NudgeType): String {
        return "${senderName}님에게 콕 찌르기(${type.factorName})가 왔습니다"
    }

    fun getRandomBody(type: NudgeType): String {
        val list = messages[type] ?: messages.values.flatten()
        return list.random()
    }
}
