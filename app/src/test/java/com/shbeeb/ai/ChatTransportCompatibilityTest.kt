package com.shbeeb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTransportCompatibilityTest {
    @Test fun newProvidersAreResolvableAndHaveSafeDefaults() {
        assertEquals(ChatEngine.DEEPSEEK, ChatEngine.fromId("deepseek"))
        assertEquals(ChatEngine.OPENROUTER, ChatEngine.fromId("openrouter"))
        assertEquals("deepseek-flash", ChatEngine.DEEPSEEK.defaultModel)
        assertEquals("openrouter/free", ChatEngine.OPENROUTER.defaultModel)
    }

    @Test fun allowsOpenRouterModelIdsButRejectsUnsafeCharacters() {
        assertEquals("deepseek/deepseek-r1:free",
            ChatTransport.checkedCloudModel(" deepseek/deepseek-r1:free "))
        assertTrue(runCatching {
            ChatTransport.checkedCloudModel("deepseek/anything?key=secret")
        }.isFailure)
        assertTrue(runCatching {
            ChatTransport.checkedCloudModel("https://example.com/a")
        }.isFailure)
    }

    @Test fun trimsOnlyOuterWhitespaceFromKey() {
        assertEquals("sk-token", ChatTransport.cleanKey("  sk-token\n"))
        assertTrue(runCatching { ChatTransport.cleanKey("sk- bad") }.isFailure)
        assertTrue(runCatching { ChatTransport.cleanKey("  ") }.isFailure)
    }

    @Test fun cleansChatWithoutMergingConsecutiveUserMessages() {
        val result = ChatTransport.normalizedHistory(listOf(
            ChatEntry("assistant", "orphan"),
            ChatEntry("user", "first"),
            ChatEntry("user", "second"),
            ChatEntry("assistant", ""),
            ChatEntry("assistant", "answer")
        ))
        assertEquals(listOf("first", "second", "answer"), result.map { it.text })
        assertEquals(listOf("user", "user", "assistant"), result.map { it.role })
    }

    @Test fun detectsCreditBalanceAndInvalidGeminiKeyWithoutExposingRawMessage() {
        val balance = ChatTransport.explainFailure(400,
            """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the API."}}""")
        val secret = "secret-credential-must-never-appear"
        val invalidKey = ChatTransport.explainFailure(400,
            """{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid: $secret"}}""")
        assertTrue(balance.contains("رصيد"))
        assertTrue(invalidKey.contains("مفتاح"))
        assertFalse(invalidKey.contains(secret))
        assertTrue(invalidKey.contains("HTTP 400"))
    }

    @Test fun supportsUnknownErrorBodiesWithoutLeakingThem() {
        val body = """{"error":{"message":"test-only-sensitive-placeholder-not-a-key"}}"""
        val msg = ChatTransport.explainFailure(500, body)
        assertTrue(msg.contains("HTTP 500"))
        assertFalse(msg.contains("test-only-sensitive-placeholder-not-a-key"))
    }
}
