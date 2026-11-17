package com.shbeeb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalResponsePolicyTest {
    @Test fun selectsSafeDefaultAndExpectedTokenBudgets() {
        assertEquals(LocalResponseMode.BALANCED, LocalResponseMode.fromId("unknown"))
        assertEquals(96, LocalResponseMode.FAST.maxOutputTokens)
        assertEquals(192, LocalResponseMode.BALANCED.maxOutputTokens)
        assertEquals(384, LocalResponseMode.DETAILED.maxOutputTokens)
    }

    @Test fun addsBriefArabicUncertaintyGuidance() {
        val result = LocalResponsePolicy.buildPrompt(" ما عاصمة سوريا؟ ", LocalResponseMode.FAST)
        assertTrue(result.contains("إذا لم تعرف"))
        assertTrue(result.contains("ولا تخمّن"))
        assertTrue(result.contains("إذا كان السؤال غامضاً"))
        assertTrue(result.contains("ما عاصمة سوريا؟"))
        assertTrue(result.contains("جملة أو جملتين"))
    }

    @Test fun rejectsBlankAndOversizedQuestions() {
        assertTrue(runCatching {
            LocalResponsePolicy.buildPrompt(" ", LocalResponseMode.BALANCED)
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            LocalResponsePolicy.buildPrompt("x".repeat(3001), LocalResponseMode.BALANCED)
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
