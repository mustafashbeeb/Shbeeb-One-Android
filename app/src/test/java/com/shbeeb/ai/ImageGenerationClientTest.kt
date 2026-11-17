package com.shbeeb.ai

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationClientTest {
    @Test fun imagePayloadHasOneLowCostPortrait() {
        val request = ImageGenerationClient.requestJson("دمشق في الربيع")
        assertEquals("gpt-image-1-mini", request.getString("model"))
        assertEquals("1024x1536", request.getString("size"))
        assertEquals("low", request.getString("quality"))
        assertEquals("png", request.getString("output_format"))
        assertEquals(1, request.getInt("n"))
        assertEquals("دمشق في الربيع", request.getString("prompt"))
    }

    @Test fun rejectsEmptyPrompt() {
        val failure = runCatching { ImageGenerationClient.requestJson("  ") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun acceptsOnlyPngBytes() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val image = JSONObject().put("data", JSONArray().put(
            JSONObject().put("b64_json", Base64.getEncoder().encodeToString(png))))
        assertTrue(ImageGenerationClient.decodeImage(image).contentEquals(png))
    }

    @Test fun rejectsWrongBinaryResponse() {
        val json = JSONObject().put("data", JSONArray().put(
            JSONObject().put("b64_json", Base64.getEncoder()
                .encodeToString("INVALID!".toByteArray()))))
        val failure = runCatching { ImageGenerationClient.decodeImage(json) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
