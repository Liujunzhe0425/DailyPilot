package com.local.dailyautomation.runtime

object SafetyClassifier {
    val securityWords = listOf("验证码", "人脸验证", "设备验证", "重新登录", "账号登录", "安全验证")
    val forbiddenWords = listOf("支付", "充值", "超级擦亮", "推广", "确认交易", "开通服务", "购买", "扣费")

    fun securityState(texts: List<String>): String? =
        if (texts.any { value -> securityWords.any(value::contains) }) "MANUAL_VERIFICATION_REQUIRED" else null

    fun forbiddenHit(texts: List<String>): String? =
        forbiddenWords.firstOrNull { word -> texts.any { it.contains(word) } }
}
