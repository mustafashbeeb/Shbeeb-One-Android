package com.shbeeb.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** Metadata for a model already stored in the user's shared document location. */
internal data class LocalModelInfo(
    val uri: Uri?,
    val displayName: String,
    val sizeBytes: Long,
    val isShared: Boolean
)

/** A seekable model descriptor. The descriptor must outlive the native engine. */
internal data class OpenedLocalModel(
    val key: String,
    val path: String,
    val descriptor: ParcelFileDescriptor
) : AutoCloseable {
    override fun close() {
        runCatching { descriptor.close() }
    }
}

/**
 * User-owned model-file references. SAF grants access to the selected file; this
 * class does not copy, download, delete, or upload shared model files.
 */
internal object LocalModelFiles {
    private const val PREFS = "shbeeb_shared_model_library"
    private const val URI_KEY = "litert_model_uri"
    private const val NAME_KEY = "litert_model_name"
    private const val SIZE_KEY = "litert_model_size"
    private const val LEGACY_MODEL_NAME = "shbeeb-local.litertlm"
    private const val MAX_SIZE = 3_500_000_000L
    private const val MIN_SIZE = 100_000L

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedUri(context: Context): Uri? = runCatching {
        preferences(context).getString(URI_KEY, null)?.let(Uri::parse)
    }.getOrNull()

    private fun legacyModelFile(context: Context) = File(context.filesDir, LEGACY_MODEL_NAME)

    fun hasLegacyModel(context: Context): Boolean =
        legacyModelFile(context).let { it.isFile && it.length() in MIN_SIZE..MAX_SIZE }

    fun isInstalled(context: Context): Boolean =
        selectedUri(context) != null || hasLegacyModel(context)

    fun info(context: Context): LocalModelInfo? {
        val prefs = preferences(context)
        val uri = selectedUri(context)
        if (uri != null) {
            val name = prefs.getString(NAME_KEY, null) ?: displayName(context, uri)
            val size = prefs.getLong(SIZE_KEY, -1L)
            return if (size in MIN_SIZE..MAX_SIZE) LocalModelInfo(uri, name, size, true) else null
        }
        val legacy = legacyModelFile(context)
        return if (hasLegacyModel(context)) {
            LocalModelInfo(null, legacy.name, legacy.length(), false)
        } else null
    }

    /** Persist a user-selected .litertlm URI after checking it is a local, seekable file. */
    fun select(context: Context, uri: Uri): LocalModelInfo {
        val name = displayName(context, uri)
        require(name.endsWith(".litertlm", ignoreCase = true)) {
            "اختر ملفاً بصيغة .litertlm فقط؛ GGUF وملفات الصور لا تعمل في LiteRT-LM."
        }
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.statSize
        } ?: throw IOException("تعذّر فتح الملف المحدد.")
        require(size in MIN_SIZE..MAX_SIZE) {
            "حجم الملف غير مدعوم أو أن موفّر الملفات لا يتيح ملفاً محلياً قابلاً للبحث. اختر ملفاً محفوظاً على الجهاز، لا رابطاً سحابياً."
        }

        val oldUri = selectedUri(context)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (failure: SecurityException) {
            throw IOException("لم يمنح منتقي Android إذن وصول مستمراً للملف. أعد اختياره من تطبيق الملفات.", failure)
        }
        preferences(context).edit()
            .putString(URI_KEY, uri.toString())
            .putString(NAME_KEY, name)
            .putLong(SIZE_KEY, size)
            .apply()
        if (oldUri != null && oldUri != uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        return LocalModelInfo(uri, name, size, true)
    }

    fun currentKey(context: Context): String? {
        selectedUri(context)?.let { return it.toString() }
        val legacy = legacyModelFile(context)
        return if (hasLegacyModel(context)) legacy.absolutePath else null
    }

    /** Open from SAF without making an app-private duplicate; keep the PFD alive. */
    internal fun openModel(context: Context): OpenedLocalModel {
        val uri = selectedUri(context)
        if (uri != null) {
            val name = displayName(context, uri)
            require(name.endsWith(".litertlm", ignoreCase = true)) {
                "الملف المرتبط لم يعد نموذج LiteRT صالح الامتداد."
            }
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("تعذّر فتح النموذج من المجلد المشترك. أعد اختيار الملف.")
            if (descriptor.statSize !in MIN_SIZE..MAX_SIZE) {
                descriptor.close()
                throw IOException("ملف النموذج غير متاح كملف محلي قابل للبحث. اختر نسخة محفوظة على الجهاز.")
            }
            return OpenedLocalModel(uri.toString(), "/proc/self/fd/${descriptor.fd}", descriptor)
        }

        // Backward compatibility with an older app-private import, if one exists.
        val legacy = legacyModelFile(context)
        if (hasLegacyModel(context)) {
            val descriptor = ParcelFileDescriptor.open(legacy, ParcelFileDescriptor.MODE_READ_ONLY)
            return OpenedLocalModel(legacy.absolutePath, "/proc/self/fd/${descriptor.fd}", descriptor)
        }
        throw IOException("لم تحدد ملف نموذج محلي. اختر ملف .litertlm من المكتبة المشتركة أولاً.")
    }

    fun clearReference(context: Context) {
        val uri = selectedUri(context)
        preferences(context).edit().remove(URI_KEY).remove(NAME_KEY).remove(SIZE_KEY).apply()
        if (uri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun deleteLegacyCopy(context: Context): Boolean {
        val legacy = legacyModelFile(context)
        return !legacy.exists() || legacy.delete()
    }

    private fun displayName(context: Context, uri: Uri): String {
        var result = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) result = cursor.getString(column) ?: result
                }
            }
        return result
    }
}

/**
 * Owns one LiteRT Engine/Conversation and the file descriptor used to load it.
 * Calls run on a worker thread; this is not a background service.
 */
internal object LocalOnDeviceEngine {
    private val lock = Any()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedKey: String? = null
    private var modelDescriptor: ParcelFileDescriptor? = null

    fun reply(
        context: Context,
        prompt: String,
        mode: LocalResponseMode = LocalResponseMode.BALANCED
    ): String = synchronized(lock) {
        require(prompt.isNotBlank()) { "اكتب رسالة لتجربتها مع نموذج الهاتف." }
        require(prompt.length <= 3000) { "النص طويل جداً للمحرك المحلي التجريبي (الحد 3000 حرف)." }
        val modelKey = LocalModelFiles.currentKey(context)
            ?: throw IllegalStateException("لم تحدد ملف نموذج محلي. اختر ملف .litertlm من الإعدادات.")
        require(LocalModelFiles.isInstalled(context)) {
            "لم يعد ملف النموذج متاحاً. أعد اختياره من المجلد المشترك."
        }

        if (engine == null || loadedKey != modelKey) {
            closeLocked()
            val opened = LocalModelFiles.openModel(context)
            var candidate: Engine? = null
            try {
                val created = Engine(
                    EngineConfig(
                        modelPath = opened.path,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                )
                candidate = created
                runBlocking { created.initialize() }
                val newConversation = created.createConversation()
                engine = created
                conversation = newConversation
                loadedKey = modelKey
                modelDescriptor = opened.descriptor
            } catch (failure: Throwable) {
                runCatching { candidate?.close() }
                opened.close()
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
        loadedKey = null
        runCatching { modelDescriptor?.close() }
        modelDescriptor = null
    }
}
