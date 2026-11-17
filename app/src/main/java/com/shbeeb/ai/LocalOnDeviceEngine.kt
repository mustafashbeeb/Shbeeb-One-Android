package com.shbeeb.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Metadata for a model already stored in the user's shared document location. */
internal data class LocalModelInfo(
    val uri: Uri?,
    val displayName: String,
    val sizeBytes: Long,
    val isShared: Boolean
)

/** A private, seekable model copy with the extension required by LiteRT-LM. */
internal data class OpenedLocalModel(
    val key: String,
    val path: String
)

/**
 * User-owned model-file references. The selected SAF document remains untouched.
 * LiteRT-LM infers format from the path extension and therefore needs an app-private
 * .litertlm working copy; that copy is cached separately and removed when unlinked.
 */
internal object LocalModelFiles {
    private const val PREFS = "shbeeb_shared_model_library"
    private const val URI_KEY = "litert_model_uri"
    private const val NAME_KEY = "litert_model_name"
    private const val SIZE_KEY = "litert_model_size"
    private const val LEGACY_MODEL_NAME = "shbeeb-local.litertlm"
    private const val WORKING_MODEL_PREFIX = "shbeeb-litert-"
    private const val COPY_RESERVE_BYTES = 128L * 1024L * 1024L
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

    /** Create/reuse an app-private, correctly suffixed copy for LiteRT-LM. */
    internal fun openModel(context: Context): OpenedLocalModel {
        val uri = selectedUri(context)
        if (uri != null) {
            val name = displayName(context, uri)
            require(name.endsWith(".litertlm", ignoreCase = true)) {
                "الملف المرتبط لم يعد نموذج LiteRT صالح الامتداد."
            }
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?: throw IOException("تعذّر فتح النموذج من المجلد المشترك. أعد اختيار الملف.")
            if (size !in MIN_SIZE..MAX_SIZE) {
                throw IOException("ملف النموذج غير متاح كملف محلي قابل للبحث. اختر نسخة محفوظة على الجهاز.")
            }
            val destination = workingModelFile(context, uri)
            if (!destination.isFile || destination.length() != size) {
                if (destination.exists() && !destination.delete()) {
                    throw IOException("تعذّر استبدال نسخة التشغيل القديمة. حرّر مساحة ثم أعد المحاولة.")
                }
                val available = context.noBackupFilesDir.usableSpace
                if (available < size + COPY_RESERVE_BYTES) {
                    val neededMb = (size + COPY_RESERVE_BYTES) / 1_048_576L
                    val availableMb = available / 1_048_576L
                    throw IOException("المساحة الداخلية غير كافية لنسخة تشغيل النموذج. يلزم نحو ${neededMb} MB ويتوفر ${availableMb} MB. سيبقى ملفك الأصلي دون تغيير.")
                }
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("تعذّر قراءة الملف المحدد. أعد اختياره من تطبيق الملفات.")
                copyModelStream(input, destination, size, available)
            }
            removeStaleWorkingCopies(context, destination)
            return OpenedLocalModel(uri.toString(), destination.absolutePath)
        }

        // Backward compatibility with an older app-private import, if one exists.
        val legacy = legacyModelFile(context)
        if (hasLegacyModel(context)) {
            return OpenedLocalModel(legacy.absolutePath, legacy.absolutePath)
        }
        throw IOException("لم تحدد ملف نموذج محلي. اختر ملف .litertlm من المكتبة المشتركة أولاً.")
    }

    /** Streaming copy keeps memory use bounded, preserves the source, and publishes only complete files. */
    internal fun copyModelStream(
        source: InputStream,
        destination: File,
        expectedSize: Long,
        availableBytes: Long
    ): File {
        require(expectedSize in MIN_SIZE..MAX_SIZE) { "حجم ملف النموذج خارج الحدود المدعومة." }
        if (availableBytes < expectedSize + COPY_RESERVE_BYTES) {
            throw IOException("المساحة الداخلية غير كافية لإنشاء نسخة تشغيل آمنة للنموذج.")
        }
        val parent = destination.parentFile ?: throw IOException("مسار نسخة النموذج غير صالح.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("تعذّر إنشاء مجلد نسخة النموذج.")
        val temporary = File(parent, destination.name + ".part")
        temporary.delete()
        var copied = 0L
        try {
            source.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > expectedSize || copied > MAX_SIZE) {
                            throw IOException("حجم الملف المقروء أكبر من الحجم المعلن؛ أوقفنا النسخ لحماية التخزين.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (copied != expectedSize || temporary.length() != expectedSize) {
                throw IOException("النسخة غير مكتملة (${copied / 1_048_576L} MB من ${expectedSize / 1_048_576L} MB). أعد المحاولة؛ لم يتغير ملفك الأصلي.")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("تعذّر استبدال نسخة النموذج الداخلية.")
            }
            if (!temporary.renameTo(destination)) throw IOException("تعذّر اعتماد نسخة النموذج بعد اكتمالها.")
            return destination
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun workingModelFile(context: Context, uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(context.noBackupFilesDir, "$WORKING_MODEL_PREFIX$digest.litertlm")
    }

    private fun removeStaleWorkingCopies(context: Context, keep: File) {
        context.noBackupFilesDir.listFiles()
            ?.filter { it.name.startsWith(WORKING_MODEL_PREFIX) && it.absolutePath != keep.absolutePath }
            ?.forEach { runCatching { it.delete() } }
    }

    internal fun pruneWorkingCopiesExceptSelected(context: Context) {
        val uri = selectedUri(context) ?: return
        removeStaleWorkingCopies(context, workingModelFile(context, uri))
    }

    private fun deleteWorkingCopies(context: Context) {
        context.noBackupFilesDir.listFiles()
            ?.filter { it.name.startsWith(WORKING_MODEL_PREFIX) }
            ?.forEach { runCatching { it.delete() } }
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
        deleteWorkingCopies(context)
    }

    fun deleteLegacyCopy(context: Context): Boolean {
        val legacy = legacyModelFile(context)
        val deleted = !legacy.exists() || legacy.delete()
        if (deleted) deleteWorkingCopies(context)
        return deleted
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
 * Owns one LiteRT Engine/Conversation and keeps its private model file on disk.
 * Calls run on a worker thread; this is not a background service.
 */
internal object LocalOnDeviceEngine {
    private val lock = Any()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedKey: String? = null

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
            } catch (failure: Throwable) {
                runCatching { candidate?.close() }
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
    }
}
