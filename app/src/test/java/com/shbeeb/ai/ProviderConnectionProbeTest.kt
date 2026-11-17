package com.shbeeb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConnectionProbeTest {
    @Test fun cloudChecksUseMetadataNotGenerativeEndpoints() {
        assertEquals(
            "https://api.openai.com/v1/models/gpt-5.4-mini",
            ProviderConnectionProbe.modelUrl(ChatEngine.OPENAI, "gpt-5.4-mini")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash",
            ProviderConnectionProbe.modelUrl(ChatEngine.GEMINI, "gemini-3.8-flash")
        )
        assertEquals(
            "https://api.anthropic.com/v1/models/claude-sonnet-4-6",
            ProviderConnectionProbe.modelUrl(ChatEngine.CLAUDE, "claude-sonnet-4-6")
        )
    }

    @Test fun probesDeepSeekAndOpenRouterWithAuthenticatedFixedEndpoints() {
        assertEquals("https://api.deepseek.com/models",
            ProviderConnectionProbe.modelUrl(ChatEngine.DEEPSEEK, "deepseek-flash"))
        assertEquals("https://openrouter.ai/api/v1/key",
            ProviderConnectionProbe.modelUrl(ChatEngine.OPENROUTER, "openrouter/free"))
        assertTrue(ProviderKeyLinks.url(ChatEngine.DEEPSEEK).startsWith("https://"))
        assertTrue(ProviderKeyLinks.url(ChatEngine.OPENROUTER).startsWith("https://"))
    }

    @Test fun cloudPathRejectsInjectedUrlsOrQueryText() {
        assertTrue(runCatching {
            ProviderConnectionProbe.modelUrl(ChatEngine.OPENAI, "gpt-mini?evil=1")
        }.isFailure)
    }

    @Test fun ollamaAcceptsOnlyLanIpAndPort() {
        assertEquals("http://192.168.1.20:11434/api/tags",
            ProviderConnectionProbe.safeOllamaUrl("http://192.168.1.20:11434"))
        assertTrue(runCatching {
            ProviderConnectionProbe.safeOllamaUrl("http://8.8.8.8:11434")
        }.isFailure)
        assertTrue(runCatching {
            ProviderConnectionProbe.safeOllamaUrl("http://localhost:11434")
        }.isFailure)
    }

    @Test fun officialKeyLinksAreHttpsAndPerProvider() {
        for (engine in listOf(ChatEngine.OPENAI, ChatEngine.GEMINI, ChatEngine.CLAUDE)) {
            assertTrue(ProviderKeyLinks.url(engine).startsWith("https://"))
        }
        assertTrue(ProviderKeyLinks.url(ChatEngine.GEMINI).contains("aistudio.google.com"))
        assertTrue(ProviderKeyLinks.url(ChatEngine.OPENAI).contains("platform.openai.com"))
    }
}
