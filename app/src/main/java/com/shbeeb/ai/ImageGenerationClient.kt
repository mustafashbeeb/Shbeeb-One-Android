package com.shbeeb.ai

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONObject

/**
 * Optional, user-confirmed cloud image generation.
 * No API secrets are persisted, logged or embedded in application resources.
 * A production version needs authenticated server mediation and quotas.
 */
internal object ImageGenerationClient {
    private const val ENDPOINT = "https://api.openai.com/v1/images/generations"
    private const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    private const val IMAGE_MODEL = "gpt-image-1-mini"

    internal fun requestJson(prompt: String): JSONObject {
        val clean = prompt.trim()
        require(clean.isNotEmpty() && clean.length <= 32000) {
            "اكتب وصفاً للصورة لا يتجاوز 32000 حرف."
        }
        return JSONObject()
            .put("model", IMAGE_MODEL)
            .put("prompt", clean)
            .put("size", "1024x1536")
            .put("quality", "low")
            .put("output_format", "png")
            .put("n", 1)
    }

    internal fun decodeImage(response: JSONObject): ByteArray {
        val base64 = response.optJSONArray("data")?.optJSONObject(0)
            ?.optString("b64_json").orEmpty()
        require(base64.isNotBlank()) { "ما وصل ملف صورة من المحرك." }
        require(base64.length < MAX_RESPONSE_BYTES) {
            "ملف الصورة أكبر من الحد المسموح للتجربة."
        }
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) {
            "حجم الصورة الناتجة غير مناسب."
        }
        // PNG magic bytes. Do not assume arbitrary provider bytes are an image.
        require(bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte()) {
            "صيغة الصورة المُستلمة غير صالحة."
        }
        return bytes
    }

    fun generate(key: String, prompt: String): ByteArray {
        require(key.isNotBlank()) { "أدخل مفتاح OpenAI API في الإعدادات أولاً." }
        val body = requestJson(prompt).toString()
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 180000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            if (status !in 200..299) {
                val safeError = c.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(4096) }.orEmpty()
                throw IllegalStateException(ChatTransport.explainFailure(status, safeError))
            }
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            c.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("استجابة الصورة كبيرة جداً لهذه النسخة.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return decodeImage(JSONObject(output.toString(Charsets.UTF_8.name())))
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("انتهت مهلة توليد الصورة. تحقق من حساب API والإنترنت.")
        } catch (e: java.io.IOException) {
            throw IllegalStateException("فشل الاتصال بخدمة الصور. تحقق من الشبكة.")
        } finally {
            c.disconnect()
        }
    }
}
