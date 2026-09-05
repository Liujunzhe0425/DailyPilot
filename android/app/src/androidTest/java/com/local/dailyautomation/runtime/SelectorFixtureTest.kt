package com.local.dailyautomation.runtime

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SelectorFixtureTest {
    private fun texts(path: String): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val json = context.assets.open(path).bufferedReader().use { it.readText() }
        return JsonParser.parseString(json).asJsonObject["nodes"].asJsonArray.map { it.asJsonObject["text"].asString }
    }

    @Test fun securityFixtureRequiresManualVerification() {
        assertEquals("MANUAL_VERIFICATION_REQUIRED", SafetyClassifier.securityState(texts("fixtures/common/security.json")))
    }

    @Test fun financialFixtureIsBlocked() {
        assertNotNull(SafetyClassifier.forbiddenHit(texts("fixtures/common/forbidden-financial.json")))
    }
}
