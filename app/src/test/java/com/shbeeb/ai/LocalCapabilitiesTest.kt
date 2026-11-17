package com.shbeeb.ai

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
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

    @Test fun privateLitertCopyIsCompleteAndUsesTheRequiredExtension() {
        val directory = Files.createTempDirectory("shbeeb-litert-test").toFile()
        try {
            val source = ByteArray(100_000) { (it % 251).toByte() }
            val destination = File(directory, "gemma-working.litertlm")
            val available = source.size.toLong() + 256L * 1024L * 1024L
            val copied = LocalModelFiles.copyModelStream(
                ByteArrayInputStream(source), destination, source.size.toLong(), available
            )

            assertTrue(copied.name.endsWith(".litertlm"))
            assertEquals(source.size.toLong(), copied.length())
            assertArrayEquals(source, copied.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun incompletePrivateCopyDoesNotReplaceAnExistingModelFile() {
        val directory = Files.createTempDirectory("shbeeb-litert-failure").toFile()
        try {
            val destination = File(directory, "gemma-working.litertlm")
            val existing = ByteArray(100_000) { 7 }
            destination.writeBytes(existing)
            val available = 100_000L + 256L * 1024L * 1024L
            val failure = runCatching {
                LocalModelFiles.copyModelStream(
                    ByteArrayInputStream(ByteArray(99_999)), destination, 100_000L, available
                )
            }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertArrayEquals(existing, destination.readBytes())
            assertFalse(File(directory, destination.name + ".part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
