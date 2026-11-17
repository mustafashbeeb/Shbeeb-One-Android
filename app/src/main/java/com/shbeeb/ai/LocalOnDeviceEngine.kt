package com.shbeeb.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

/**
 * An optional real on-device LiteRT-LM backend for compatible .litertlm models.
 * No model is bundled in the APK; the user explicitly imports a licensed file.
 * CPU-only for compatibility with mid-range Android devices.
 */
internal object LocalModelFiles {
    private const val MODEL_NAME = "shbeeb-local.litertlm"
    private const val TEMP_NAME = "shbeeb-local.part"
    private const val MAX_SIZE = 3_500_000_000L
    private const val MIN_SIZE = 100_000L

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_NAME)

    fun isInstalled(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() >= MIN_SIZE }

    fun import(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        var fileName = ""
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) fileName = cursor.getString(0).orEmpty()
            }
        require(fileName.endsWith(".litertlm", ignoreCase = true)) {
            "اختر ملف نموذج بصيغة .litertlm فقط، وليس GGUF أو ZIP."
        }
        val dir = context.filesDir
        val temp = File(dir, TEMP_NAME)
        var bytes = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= MAX_SIZE) {
                            "الملف كبير جداً لهذا الإصدار. اختر نموذجاً أصغر."
                        }
                        // Leave some space on internal storage for SQLite, logs and Android.
                        require(dir.usableSpace > 250_000_000L) {
                            "المساحة الداخلية غير كافية لاستيراد النموذج."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: error("لم يُسمح بفتح الملف.")
            require(bytes >= MIN_SIZE) { "الملف صغير جداً ليكون نموذجاً صالحاً." }
            // Never replace a previously working model unless the new import completed.
            val target = modelFile(context)
            require(temp.renameTo(target)) { "تعذّر تثبيت النموذج بعد نسخه." }
            return bytes
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}

/**
 * All initialization/generation/close operations must be called from the
 * background; synchronized protects the single Engine/Conversation owner.
 * In particular, this is NOT a service and does not listen in the background.
 */
internal object LocalOnDeviceEngine {
    private val lock = Any()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    fun reply(
        context: Context,
        prompt: String,
        mode: LocalResponseMode = LocalResponseMode.BALANCED
    ): String = synchronized(lock) {
        require(prompt.isNotBlank()) { "اكتب رسالة لتجربتها مع نموذج الهاتف." }
        require(prompt.length <= 3000) { "النص طويل جداً للمحرك المحلي التجريبي (الحد 3000 حرف)." }
        val model = LocalModelFiles.modelFile(context)
        require(LocalModelFiles.isInstalled(context)) {
            "لم تثبّت نموذجاً محلياً. اختر ملف .litertlm من الإعدادات."
        }
        if (engine == null || loadedPath != model.absolutePath) {
            closeLocked()
            val config = EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            val candidate = Engine(config)
            // Initialize on the caller's worker thread; clean up native resources on failures.
            try {
                runBlocking { candidate.initialize() }
                val newConversation = candidate.createConversation()
                engine = candidate
                conversation = newConversation
                loadedPath = model.absolutePath
            } catch (failure: Throwable) {
                runCatching { candidate.close() }
                throw failure
            }
        }
        val active = conversation ?: error("لم يبدأ نموذج الهاتف محادثة.")
        val preparedPrompt = LocalResponsePolicy.buildPrompt(prompt, mode)
        val response = runBlocking {
            active.sendMessage(preparedPrompt, maxOutputToken = mode.maxOutputTokens)
        }
        val answer = response.toString().trim()
        if (answer.isBlank()) error("النموذج المحلي لم ينتج جواباً نصياً.")
        answer
    }

    fun reset() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }
}
