package com.shbeeb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCapabilitiesTest {
    @Test fun localEngineIsDistinctFromComputerOllama() {
        assertEquals(ChatEngine.LOCAL, ChatEngine.fromId("local"))
        assertEquals(ChatEngine.OLLAMA, ChatEngine.fromId("ollama"))
        assertTrue(ChatEngine.LOCAL.title != ChatEngine.OLLAMA.title)
    }

    @Test fun localEngineDoesNotTryToUseACloudApiKey() {
        assertFalse(ChatTransport.configured(
            ChatEngine.LOCAL, "a-secret-key", "http://192.168.1.2:11434", "llama3.2:1b"
        ))
        val error = runCatching {
            ChatTransport.reply(
                ChatEngine.LOCAL, "", listOf(ChatEntry("user", "مرحبا")),
                "", "", ""
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test fun localVideoHasVerticalEightSecondGeometry() {
        assertEquals(9, LocalVideoMaker.WIDTH / 64)
        assertEquals(16, LocalVideoMaker.HEIGHT / 64)
        assertEquals(8, LocalVideoMaker.SECONDS)
        assertEquals(96, LocalVideoMaker.FPS * LocalVideoMaker.SECONDS)
    }
}
