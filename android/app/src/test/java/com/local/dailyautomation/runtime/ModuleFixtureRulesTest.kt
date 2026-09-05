package com.local.dailyautomation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleFixtureRulesTest {
    @Test fun weihudaStatesAreIdempotent() {
        assertEquals("COMPLETED_TODAY", ModuleFixtureRules.weihuda(listOf("当前积分", "已签")))
        assertEquals("NOT_COMPLETED", ModuleFixtureRules.weihuda(listOf("当前积分", "签到")))
        assertEquals("MANUAL_VERIFICATION_REQUIRED", ModuleFixtureRules.weihuda(listOf("设备安全验证")))
    }
    @Test fun couponMustBeUniqueAndTaskFree() {
        assertEquals("NOT_COMPLETED", ModuleFixtureRules.coupon(listOf("20提现券 领取")))
        assertEquals("UNKNOWN", ModuleFixtureRules.coupon(listOf("20提现券 领取", "10提现券 领取")))
        assertEquals("UNKNOWN", ModuleFixtureRules.coupon(listOf("购物领取提现券 支付")))
    }
    @Test fun xianyuPaidBoundaryWinsWithoutFreeExactTarget() {
        assertEquals("FORBIDDEN_UI", ModuleFixtureRules.xianyu(listOf("试试超级擦亮", "推广")))
        assertEquals("NOT_COMPLETED", ModuleFixtureRules.xianyu(listOf("我发布的", "今日数据", "一键擦亮", "+5曝光")))
        assertEquals("COMPLETED_TODAY", ModuleFixtureRules.xianyu(listOf("我发布的", "今日数据", "试试超级擦亮", "有机会当天出单")))
    }
}
