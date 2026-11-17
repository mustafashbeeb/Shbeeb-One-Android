package com.shbeeb.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prototype transport for four separately selectable providers.
 * Never bake API keys into a distributable APK. Before selling the app,
 * migrate cloud calls and key management to a trusted authenticated backend.
 */
internal data class ChatEntry(val role: String, val text: String)

internal enum class ChatEngine(val id: String, val title: String, val hint: String, val defaultModel: String) {
    OPENAI("openai", "OpenAI", "سحابي • يحتاج مفتاح API", "gpt-5.4-mini"),
    CLAUDE("claude", "Claude", "سحابي • يحتاج مفتاح API", "claude-sonnet-4-6"),
    GEMINI("gemini", "Gemini", "سحابي • يحتاج مفتاح API", "gemini-3.8-flash"),
    DEEPSEEK("deepseek", "DeepSeek", "سحابي • مدفوع حسب الاستخدام", "deepseek-flash"),
    OPENROUTER("openrouter", "OpenRouter", "سحابي • نماذج مجانية محدودة", "openrouter/free"),
    OLLAMA("ollama", "Ollama", "محلي على كمبيوتر متصل بالشبكة • بلا مفتاح", "llama3.2:1b"),
    LOCAL("local", "LiteRT-LM", "محلي • نموذج محادثة .litertlm على الهاتف", "");

    companion object {
        fun fromId(id: String): ChatEngine = values().firstOrNull { it.id == id } ?: OPENAI
    }
}

internal object ChatTransport {
    internal fun normalizedHistory(history: List<ChatEntry>): List<ChatEntry> =
        history.filter { it.text.isNotBlank() }.takeLast(12)
            .dropWhile { it.role == "assistant" }
            .map { ChatEntry(if (it.role == "assistant") "assistant" else "user", it.text) }

    private fun historyJson(history: List<ChatEntry>): JSONArray {
        val messages = JSONArray()
        normalizedHistory(history).forEach {
            messages.put(JSONObject()
                .put("role", if (it.role == "assistant") "assistant" else "user")
                .put("content", it.text))
        }
        return messages
    }

    fun configured(engine: ChatEngine, key: String, ollamaBase: String, ollamaModel: String): Boolean =
        when (engine) {
            ChatEngine.LOCAL -> false // Determined by the installed on-device model in Android UI.
            ChatEngine.OLLAMA -> ollamaBase.isNotBlank() && ollamaModel.isNotBlank()
            else -> key.isNotBlank()
        }

    fun reply(
        engine: ChatEngine,
        key: String,
        history: List<ChatEntry>,
        ollamaBase: String,
        ollamaModel: String,
        cloudModel: String,
        diagnostic: Boolean = false
    ): String = when (engine) {
        ChatEngine.OPENAI -> openai(key, history, cloudModel, diagnostic)
        ChatEngine.CLAUDE -> claude(key, history, cloudModel, diagnostic)
        ChatEngine.GEMINI -> gemini(key, history, cloudModel, diagnostic)
        ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER -> openAiCompatible(engine, key, history, cloudModel, diagnostic)
        ChatEngine.OLLAMA -> ollama(ollamaBase, ollamaModel, history)
        ChatEngine.LOCAL -> error("المحرك المحلي يعمل عبر LocalOnDeviceEngine، وليس عبر شبكة.")
    }

    private fun request(
        url: String,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap(),
        allowLocalHttp: Boolean = false
    ): JSONObject {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != "https") {
            val host = parsedUrl.host
            val segments = host.split(".")
            val isLan = if (segments.size == 4) {
                val nums = segments.map { it.toIntOrNull() }
                val first = nums[0] ?: -1
                val second = nums[1] ?: -1
                nums.all { it != null && it in 0..255 } &&
                    (first == 10 || first == 192 && second == 168 ||
                    first == 172 && second in 16..31 || first == 127)
            } else false
            require(allowLocalHttp && parsedUrl.protocol == "http" && isLan) {
                "اتصال Ollama عبر HTTP مسموح فقط لعنوان IP محلي؛ لا تدخل عنواناً عاماً."
            }
        }
        val connection = parsedUrl.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 90000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Classify provider error codes without showing raw responses or credentials.
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(4096) }.orEmpty()
                throw IllegalStateException(explainFailure(code, errorBody))
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("انتهت مهلة الاتصال. تأكد من الشبكة أو تشغيل النموذج.")
        } catch (_: UnknownHostException) {
            throw IllegalStateException("تعذّر الوصول إلى الخادم (DNS أو لا إنترنت).")
        } catch (_: SSLException) {
            throw IllegalStateException("فشل اتصال HTTPS الآمن؛ راجع وقت الهاتف والشبكة.")
        } catch (e: IOException) {
            throw IllegalStateException("فشل الاتصال (${e.javaClass.simpleName}). تحقق من الشبكة.")
        } catch (_: JSONException) {
            throw IllegalStateException("استجاب الخادم، لكن الرد ليس JSON صالحاً.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * HTTP 429 does not always mean insufficient funds: providers also use it
     * for short-term per-minute limits. Never automatically retry paid requests.
     */
    internal fun explainFailure(status: Int, rawBody: String): String {
        val error = runCatching { JSONObject(rawBody).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code").orEmpty().lowercase()
        val type = error?.optString("type").orEmpty().lowercase()
        val serviceStatus = error?.optString("status").orEmpty().lowercase()
        val reason = "$code $type $serviceStatus ${error?.optString("message").orEmpty().lowercase()}"
        // Never show raw error.message: some API responses echo credentials.
        val tag = listOf(code, type, serviceStatus).firstOrNull {
            it.matches(Regex("[a-z0-9_.-]{1,48}"))
        }
        val suffix = " [HTTP $status" + (tag?.let { "/$it" } ?: "") + "]"
        val message = when {
            status == 401 || reason.contains("invalid_api_key") ||
                reason.contains("api key not valid") || reason.contains("api_key_invalid") ||
                reason.contains("authentication_error") ->
                "مفتاح API غير صحيح أو منتهي. راجع المفتاح."
            status == 402 || reason.contains("insufficient_quota") ||
                reason.contains("credit balance") || reason.contains("credit_balance") ||
                reason.contains("billing_hard_limit") || reason.contains("insufficient funds") ->
                "رصيد API غير كافٍ أو تجاوز الحساب حد الإنفاق."
            status == 403 || reason.contains("permission_denied") ->
                "الخدمة رفضت الوصول. راجع صلاحيات الحساب والمنطقة."
            status == 429 -> "بلغت حدّ الطلبات أو الحصة المؤقتة (429)."
            status == 404 || reason.contains("model_not_found") ->
                "النموذج أو مسار API غير موجود أو غير متاح للحساب."
            status == 400 -> "رفضت الخدمة صيغة الطلب (400). راجع اسم النموذج وإعداداته."
            status == 408 || status == 504 -> "انتهت مهلة الخدمة."
            status in 500..599 -> "الخدمة متوقفة مؤقتاً."
            else -> "فشل اتصال المحرّك."
        }
        return message + suffix
    }

    internal fun checkedCloudModel(model: String): String {
        val id = model.trim()
        require(id.matches(Regex("[A-Za-z0-9._:/-]{1,128}")) &&
            !id.contains("://") && !id.contains("..") && !id.startsWith("/")) {
            "اسم النموذج غير صالح. استخدم الحروف والأرقام و - _ . / : فقط."
        }
        return id
    }

    internal fun cleanKey(key: String): String {
        val clean = key.trim()
        require(clean.isNotEmpty() && clean.none { it.isWhitespace() || it.code < 32 || it.code == 127 }) {
            "المفتاح فارغ أو يحتوي فراغات داخلية غير صالحة. انسخه من لوحة المزود."
        }
        return clean
    }

    private fun openai(key: String, history: List<ChatEntry>, model: String, diagnostic: Boolean): String {
        require(key.isNotBlank()) { "أضف مفتاح OpenAI API من الإعدادات." }
        val payload = JSONObject()
            .put("model", checkedCloudModel(model))
            .put("instructions", "أجب بالعربية بوضوح ما لم يطلب المستخدم غير ذلك.")
            .put("input", historyJson(history))
            .put("store", false)
        if (diagnostic) payload.put("max_output_tokens", 128)
        val response = request(
            "https://api.openai.com/v1/responses", payload,
            mapOf("Authorization" to "Bearer ${cleanKey(key)}")
        )
        val output = response.optJSONArray("output") ?: JSONArray()
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val element = content.optJSONObject(j) ?: continue
                if (element.optString("type") == "output_text") {
                    element.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
        }
        return parts.joinToString("\n").ifBlank { error("لم يصل رد نصي من OpenAI.") }
    }

    private fun claude(key: String, history: List<ChatEntry>, model: String, diagnostic: Boolean): String {
        require(key.isNotBlank()) { "أضف مفتاح Claude API من الإعدادات." }
        val payload = JSONObject()
            .put("model", checkedCloudModel(model))
            .put("max_tokens", if (diagnostic) 128 else 1024)
            .put("messages", historyJson(history))
        val response = request("https://api.anthropic.com/v1/messages", payload,
            mapOf("x-api-key" to cleanKey(key), "anthropic-version" to "2023-06-01"))
        val parts = response.optJSONArray("content") ?: JSONArray()
        val texts = mutableListOf<String>()
        for (i in 0 until parts.length()) {
            val item = parts.optJSONObject(i) ?: continue
            if (item.optString("type") == "text") {
                item.optString("text").takeIf { it.isNotBlank() }?.let(texts::add)
            }
        }
        return texts.joinToString("\n").ifBlank { error("لم يصل رد نصي من Claude.") }
    }

    private fun gemini(key: String, history: List<ChatEntry>, model: String, diagnostic: Boolean): String {
        require(key.isNotBlank()) { "أضف مفتاح Gemini API من الإعدادات." }
        val contents = JSONArray()
        normalizedHistory(history).forEach {
            contents.put(JSONObject()
                .put("role", if (it.role == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val checkedModel = checkedCloudModel(model)
        val response = request(
            "https://generativelanguage.googleapis.com/v1beta/models/$checkedModel:generateContent",
            JSONObject().put("contents", contents).apply {
                if (diagnostic) put("generationConfig", JSONObject().put("maxOutputTokens", 128))
            },
            mapOf("x-goog-api-key" to cleanKey(key))
        )
        val candidates = response.optJSONArray("candidates") ?: JSONArray()
        val content = candidates.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        val texts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            content.optJSONObject(i)?.optString("text")
                ?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        return texts.joinToString("\n").ifBlank { error("لم يصل رد نصي من Gemini.") }
    }

    /** Fixed trusted endpoints: saved keys never go to a user-supplied host. */
    private fun openAiCompatible(
        engine: ChatEngine, key: String, history: List<ChatEntry>,
        model: String, diagnostic: Boolean
    ): String {
        val endpoint = when (engine) {
            ChatEngine.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
            ChatEngine.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            else -> error("مزود غير متوافق.")
        }
        val messages = JSONArray().put(JSONObject()
            .put("role", "system").put("content", "أجب بالعربية بوضوح ما لم يطلب المستخدم غير ذلك."))
        normalizedHistory(history).forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.text))
        }
        val payload = JSONObject()
            .put("model", checkedCloudModel(model))
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", if (diagnostic) 128 else 1024)
        if (engine == ChatEngine.DEEPSEEK) {
            payload.put("thinking", JSONObject().put("type", "disabled"))
        }
        val response = request(endpoint, payload,
            mapOf("Authorization" to "Bearer ${cleanKey(key)}"))
        val content = response.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.opt("content")
        val reply = when (content) {
            is String -> content
            is JSONArray -> {
                val texts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    content.optJSONObject(i)?.optString("text")
                        ?.takeIf { it.isNotBlank() }?.let(texts::add)
                }
                texts.joinToString("\n")
            }
            else -> ""
        }
        return reply.takeIf { it.isNotBlank() }
            ?: error("وصل رد من ${engine.title} دون محتوى نصي.")
    }

    private fun ollama(base: String, model: String, history: List<ChatEntry>): String {
        require(base.isNotBlank() && model.isNotBlank()) {
            "أضف عنوان جهاز Ollama واسم النموذج في الإعدادات."
        }
        val cleanBase = base.trim().trimEnd('/')
        val payload = JSONObject()
            .put("model", model.trim())
            .put("messages", historyJson(history))
            .put("stream", false)
        val result = request("$cleanBase/api/chat", payload, allowLocalHttp = true)
        return result.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: error("لم يصل رد من Ollama. تأكد من تشغيل النموذج.")
    }
}
