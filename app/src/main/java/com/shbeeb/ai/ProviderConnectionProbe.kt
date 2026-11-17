package com.shbeeb.ai

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException
import java.net.URL
import org.json.JSONObject

/**
 * Read-only connectivity tests initiated explicitly by the user.
 * GET model metadata does not generate tokens, but cannot prove billing/quota
 * eligibility. Do not silently make paid text generation calls as a "test".
 */
internal object ProviderConnectionProbe {
    internal data class Result(val passed: Boolean, val message: String)

    internal fun modelUrl(engine: ChatEngine, model: String): String {
        require(model == ChatTransport.checkedCloudModel(model)) {
            "اسم النموذج غير صالح."
        }
        return when (engine) {
            ChatEngine.OPENAI -> "https://api.openai.com/v1/models/$model"
            ChatEngine.CLAUDE -> "https://api.anthropic.com/v1/models/$model"
            ChatEngine.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/$model"
            ChatEngine.DEEPSEEK -> "https://api.deepseek.com/models"
            ChatEngine.OPENROUTER -> "https://openrouter.ai/api/v1/key"
            else -> error("هذا المحرك لا يدعم فحص نموذج سحابي.")
        }
    }

    internal fun safeOllamaUrl(base: String): String {
        val url = URL(base.trim().trimEnd('/'))
        require(url.protocol == "http" && url.userInfo == null &&
            (url.path.isBlank() || url.path == "/") && url.query == null &&
            url.ref == null && url.port in 1..65535) {
            "استخدم عنوان Ollama محلي مثل http://192.168.1.10:11434"
        }
        val n = url.host.split(".").map { it.toIntOrNull() }
        require(n.size == 4 && n.all { it != null && it in 0..255 }) {
            "استخدم عنوان IP محلي مباشر وليس عنوان موقع عام."
        }
        val a = n[0] ?: -1
        val b = n[1] ?: -1
        require(a == 10 || a == 192 && b == 168 ||
            a == 172 && b in 16..31 || a == 127) {
            "لا يمكن استخدام خادم Ollama بعنوان عام عبر HTTP."
        }
        return url.toString().trimEnd('/') + "/api/tags"
    }

    fun check(
        context: Context,
        engine: ChatEngine,
        secret: String,
        cloudModel: String,
        ollamaHost: String,
        ollamaModel: String
    ): Result {
        if (engine == ChatEngine.LOCAL) {
            return if (LocalModelFiles.isInstalled(context)) {
                Result(true, "ملف النموذج موجود. ما زلنا بحاجة لتجربة الرد دون إنترنت للتأكد من تشغيله.")
            } else {
                Result(false, "ما فيه نموذج محلي مثبت. استورد ملف .litertlm من الإعدادات.")
            }
        }
        if (engine == ChatEngine.OLLAMA) {
            if (ollamaHost.isBlank() || ollamaModel.isBlank()) {
                return Result(false, "أدخل عنوان كمبيوتر Ollama واسم النموذج أولاً.")
            }
            return runCatching {
                val json = fetch(safeOllamaUrl(ollamaHost), emptyMap())
                val models = json.optJSONArray("models")
                val installed = (0 until (models?.length() ?: 0)).any { i ->
                    val item = models?.optJSONObject(i)
                    item?.optString("name") == ollamaModel.trim() ||
                        item?.optString("model") == ollamaModel.trim()
                }
                if (installed) Result(true, "خادم Ollama يردّ والنموذج موجود عليه. جرّب محادثة قصيرة.")
                else Result(false, "خادم Ollama يردّ لكن النموذج المطلوب غير مثبت عليه.")
            }.getOrElse { Result(false, it.message ?: "تعذّر الوصول إلى خادم Ollama.") }
        }
        if (secret.isBlank()) return Result(false, "لا يوجد مفتاح ${engine.title} API محفوظ.")
        return runCatching {
            val checkedSecret = ChatTransport.cleanKey(secret)
            val headers = when (engine) {
                ChatEngine.OPENAI, ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER ->
                    mapOf("Authorization" to "Bearer $checkedSecret")
                ChatEngine.CLAUDE -> mapOf(
                    "x-api-key" to checkedSecret, "anthropic-version" to "2023-06-01"
                )
                ChatEngine.GEMINI -> mapOf("x-goog-api-key" to checkedSecret)
                else -> emptyMap()
            }
            val data = fetch(modelUrl(engine, cloudModel.trim()), headers)
            if (engine == ChatEngine.DEEPSEEK) {
                val models = data.optJSONArray("data")
                val exists = (0 until (models?.length() ?: 0)).any { index ->
                    models?.optJSONObject(index)?.optString("id") == cloudModel.trim()
                }
                if (!exists) return@runCatching Result(false, "مفتاح DeepSeek مقبول، لكن النموذج المحدد غير موجود بقائمة حسابك.")
            }
            if (engine == ChatEngine.OPENROUTER && data.optJSONObject("data") == null) {
                return@runCatching Result(false, "OpenRouter استجاب لكن معلومات المفتاح غير واضحة.")
            }
            val note = if (engine == ChatEngine.OPENROUTER)
                "مفتاح OpenRouter مقبول؛ لم يُفحص النموذج. قد تنتهي الحصة المجانية."
            else "تم الوصول إلى حساب ${engine.title} والنموذج. لا يثبت هذا توفر الرصيد أو نجاح التوليد."
            Result(true, note + " استخدم اختبار رسالة فعلية بعد الموافقة على التكلفة المحتملة.")
        }.getOrElse { Result(false, it.message ?: "تعذّر فحص الخدمة.") }
    }

    private fun fetch(url: String, headers: Map<String, String>): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 12000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            headers.forEach { (name, value) -> c.setRequestProperty(name, value) }
            val status = c.responseCode
            if (status !in 200..299) {
                val error = c.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(4096) }.orEmpty()
                throw IllegalStateException(ChatTransport.explainFailure(status, error))
            }
            val json = c.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(65536)
            }
            return JSONObject(json)
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("انتهت مهلة اختبار الاتصال. تحقق من الإنترنت أو الخادم.")
        } catch (_: UnknownHostException) {
            throw IllegalStateException("تعذّر الوصول للخادم (DNS/إنترنت).")
        } catch (_: SSLException) {
            throw IllegalStateException("فشل الاتصال المشفر؛ تحقق من الشبكة ووقت الجهاز.")
        } catch (e: IOException) {
            throw IllegalStateException("خطأ شبكة (${e.javaClass.simpleName}).")
        } catch (_: JSONException) {
            throw IllegalStateException("الرد ليس JSON صالحاً.")
        } finally {
            c.disconnect()
        }
    }
}
