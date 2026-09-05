package com.local.dailyautomation.runtime

object ModuleFixtureRules {
    fun weihuda(texts: List<String>) = when {
        SafetyClassifier.securityState(texts) != null -> "MANUAL_VERIFICATION_REQUIRED"
        "已签" in texts -> "COMPLETED_TODAY"
        "签到" in texts -> "NOT_COMPLETED"
        else -> "UNKNOWN"
    }

    fun coupon(cards: List<String>) = when {
        cards.count { it.contains("提现券") && it.contains("已领取") } == 1 -> "COMPLETED_TODAY"
        cards.count { it.contains("提现券") && it.contains("领取") && listOf("购物", "游戏", "视频", "支付", "充值").none(it::contains) } == 1 -> "NOT_COMPLETED"
        else -> "UNKNOWN"
    }

    fun xianyu(texts: List<String>) = when {
        texts.any { it == "今日已擦亮" || it == "擦亮成功" } -> "COMPLETED_TODAY"
        "今日数据" in texts && "试试超级擦亮" in texts && "一键擦亮" !in texts -> "COMPLETED_TODAY"
        "今日数据" in texts && "一键擦亮" in texts && "+5曝光" in texts -> "NOT_COMPLETED"
        texts.any { value -> listOf("超级擦亮", "推广", "支付", "充值", "扣费", "权益").any(value::contains) } -> "FORBIDDEN_UI"
        else -> "UNKNOWN"
    }
}
