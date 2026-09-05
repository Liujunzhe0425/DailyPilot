package com.local.dailyautomation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModuleCatalogTest {

    @Test
    fun `catalog preserves configured order and filters disabled modules`() {
        val catalog = ModuleCatalog(
            listOf(
                ModuleDescriptor("xianyu-polish", "闲鱼免费擦亮", "com.taobao.idlefish", true, 2, "modules/xianyu-polish.js"),
                ModuleDescriptor("weihuda-sign", "湖南大学微生活", "com.tencent.mm", false, 0, "modules/weihuda-sign.js"),
                ModuleDescriptor("withdrawal-coupon", "提现笔笔省", "com.tencent.mm", true, 1, "modules/withdrawal-coupon.js"),
            )
        )

        assertEquals(
            listOf("withdrawal-coupon", "xianyu-polish"),
            catalog.enabledInRunOrder().map { it.id },
        )
    }

    @Test
    fun `manifest loader preserves scripts and rejects duplicate ids`() {
        val json = """
            [
              {"id":"weihuda-sign","displayName":"湖南大学微生活","targetPackage":"com.tencent.mm","enabled":true,"sortOrder":0,"script":"modules/weihuda-sign.js"},
              {"id":"withdrawal-coupon","displayName":"提现笔笔省","targetPackage":"com.tencent.mm","enabled":true,"sortOrder":1,"script":"modules/withdrawal-coupon.js"}
            ]
        """.trimIndent()

        assertEquals("modules/weihuda-sign.js", ModuleCatalog.load(json).all().first().script)
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCatalog.load(json.replace("withdrawal-coupon", "weihuda-sign"))
        }
    }
}
