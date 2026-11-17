package com.anythingllm.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SAF bridge for user-owned GGUF files. Models selected from shared storage are
 * opened read-only and kept alive via ParcelFileDescriptor while llama.rn uses the
 * /proc/self/fd path. New model downloads can be streamed directly into the
 * user-selected shared folder, without an app-private duplicate.
 */
class SharedModelAccessModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    companion object {
        private const val TAG = "SharedModelAccess"
        private const val PICK_GGUF_REQUEST = 7831
        private const val PICK_FOLDER_REQUEST = 7832
        private const val MIN_SIZE_BYTES = 1_000_000L
        private const val MAX_SIZE_BYTES = 20_000_000_000L
        private const val PREFS = "shbeeb_shared_model_library"
        private const val TREE_URI_KEY = "gguf_library_tree_uri"
        private const val EVENT_PROGRESS = "SharedModelDownloadProgress"
        private const val EVENT_COMPLETE = "SharedModelDownloadComplete"
        private const val EVENT_ERROR = "SharedModelDownloadError"
    }

    private class DownloadJob(val id: String) {
        val cancelled = AtomicBoolean(false)
        @Volatile var connection: HttpURLConnection? = null
    }

    private val openDescriptors = ConcurrentHashMap<String, ParcelFileDescriptor>()
    private val downloads = ConcurrentHashMap<String, DownloadJob>()
    @Volatile private var pickerPromise: Promise? = null
    @Volatile private var folderPickerPromise: Promise? = null

    init {
        reactContext.addActivityEventListener(this)
    }

    override fun getName(): String = "SharedModelAccess"

    /** Open Android's file picker. The user explicitly chooses a local GGUF file. */
    @ReactMethod
    fun pickGGUF(promise: Promise) {
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("ERR_GGUF_PICKER", "لا توجد نافذة Android نشطة لفتح منتقي الملفات.")
            return
        }
        if (pickerPromise != null || folderPickerPromise != null) {
            promise.reject("ERR_GGUF_PICKER_BUSY", "منتقي الملفات مفتوح بالفعل.")
            return
        }
        try {
            pickerPromise = promise
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            activity.startActivityForResult(intent, PICK_GGUF_REQUEST)
        } catch (error: Exception) {
            pickerPromise = null
            promise.reject("ERR_GGUF_PICKER", error.message, error)
        }
    }

    /** Ask the user to choose the shared folder where future GGUF downloads will be written. */
    @ReactMethod
    fun pickSharedFolder(promise: Promise) {
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("ERR_GGUF_FOLDER", "لا توجد نافذة Android نشطة لاختيار المجلد.")
            return
        }
        if (pickerPromise != null || folderPickerPromise != null) {
            promise.reject("ERR_GGUF_PICKER_BUSY", "منتقي الملفات مفتوح بالفعل.")
            return
        }
        try {
            folderPickerPromise = promise
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            activity.startActivityForResult(intent, PICK_FOLDER_REQUEST)
        } catch (error: Exception) {
            folderPickerPromise = null
            promise.reject("ERR_GGUF_FOLDER", error.message, error)
        }
    }

    /** Return the saved user-chosen folder URI, or null if none or its grant was revoked. */
    @ReactMethod
    fun getSharedFolder(promise: Promise) {
        try {
            promise.resolve(getStoredTreeUri()?.toString())
        } catch (error: Exception) {
            promise.reject("ERR_GGUF_FOLDER", error.message, error)
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICK_FOLDER_REQUEST -> handleFolderResult(resultCode, data)
            PICK_GGUF_REQUEST -> handleModelResult(resultCode, data)
        }
    }

    private fun handleFolderResult(resultCode: Int, data: Intent?) {
        val promise = folderPickerPromise ?: return
        folderPickerPromise = null
        if (resultCode != Activity.RESULT_OK) {
            promise.resolve(null)
            return
        }
        val uri = data?.data
        if (uri == null) {
            promise.reject("ERR_GGUF_FOLDER", "لم يُرجع منتقي Android مجلداً.")
            return
        }
        try {
            val wanted = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            require(data.flags and wanted == wanted) { "لم يمنح Android إذني القراءة والكتابة للمجلد." }
            reactContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val prefs = reactContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(TREE_URI_KEY, uri.toString()).apply()
            promise.resolve(uri.toString())
        } catch (error: Exception) {
            Log.w(TAG, "Could not persist shared-folder access", error)
            promise.reject("ERR_GGUF_FOLDER", error.message ?: "تعذّر حفظ إذن المجلد.", error)
        }
    }

    private fun handleModelResult(resultCode: Int, data: Intent?) {
        val promise = pickerPromise ?: return
        pickerPromise = null
        if (resultCode != Activity.RESULT_OK) {
            promise.resolve(null)
            return
        }
        val uri = data?.data
        if (uri == null) {
            promise.reject("ERR_GGUF_PICKER", "لم يُرجع منتقي Android ملفاً.")
            return
        }

        var granted = false
        try {
            require(data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                "لم يمنح منتقي Android صلاحية قراءة مستمرة للملف."
            }
            reactContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            granted = true
            val name = displayName(uri)
            require(name.endsWith(".gguf", ignoreCase = true)) {
                "اختر ملف نموذج محادثة بامتداد .gguf. ملفات .litertlm أو نماذج الصور لا تعمل في llama.rn."
            }
            val size = reactContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?: throw IOException("تعذّر فتح الملف المحدد.")
            require(size in MIN_SIZE_BYTES..MAX_SIZE_BYTES) {
                "حجم ملف GGUF غير صالح أو يتجاوز الحد المدعوم (20 GB)."
            }
            promise.resolve(Arguments.createMap().apply {
                putString("uri", uri.toString())
                putString("name", name)
                putDouble("sizeBytes", size.toDouble())
            })
        } catch (error: Exception) {
            if (granted) runCatching {
                reactContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Log.w(TAG, "Could not accept selected GGUF", error)
            promise.reject("ERR_GGUF_PICKER", error.message ?: "تعذّر استخدام ملف GGUF المحدد.", error)
        }
    }

    /** Open an already-granted URI and return a process-local file-descriptor path. */
    @ReactMethod
    fun openGGUF(uriValue: String, promise: Promise) {
        try {
            val uri = Uri.parse(uriValue)
            require(displayName(uri).endsWith(".gguf", ignoreCase = true)) { "الملف المحدد ليس بصيغة GGUF." }
            val descriptor = reactContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("تعذّر فتح نموذج GGUF من المجلد المشترك. أعد اختياره.")
            try {
                val size = descriptor.statSize
                require(size in MIN_SIZE_BYTES..MAX_SIZE_BYTES) { "حجم ملف GGUF غير صالح أو يتجاوز الحد المدعوم." }
                val path = "/proc/self/fd/${descriptor.fd}"
                val magic = ByteArray(4)
                FileInputStream(path).use { input ->
                    val count = input.read(magic)
                    require(count == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                        "محتوى الملف لا يحمل ترويسة GGUF صحيحة."
                    }
                }
                val token = UUID.randomUUID().toString()
                openDescriptors[token] = descriptor
                promise.resolve(Arguments.createMap().apply {
                    putString("token", token)
                    putString("path", path)
                    putDouble("sizeBytes", size.toDouble())
                })
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not open shared GGUF", error)
            promise.reject("ERR_GGUF_OPEN", error.message ?: "تعذّر فتح ملف GGUF.", error)
        }
    }

    /** Release a model descriptor after llama.rn has unloaded its native context. */
    @ReactMethod
    fun closeGGUF(token: String, promise: Promise) {
        val descriptor = openDescriptors.remove(token)
        if (descriptor == null) {
            promise.resolve(false)
            return
        }
        try {
            descriptor.close()
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("ERR_GGUF_CLOSE", error.message, error)
        }
    }

    /** Remove Shbeeb One's persisted read grant; the user-owned document is not deleted. */
    @ReactMethod
    fun releaseGGUF(uriValue: String, promise: Promise) {
        try {
            reactContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriValue), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("ERR_GGUF_RELEASE", error.message, error)
        }
    }

    /** Start streaming a model into the chosen SAF folder. Progress and result arrive as native events. */
    @ReactMethod
    fun startSharedDownload(downloadId: String, urlValue: String, fileNameValue: String, expectedSize: Double, promise: Promise) {
        var registeredJob: DownloadJob? = null
        try {
            require(downloadId.isNotBlank()) { "معرّف التنزيل غير صالح." }
            val url = URL(urlValue)
            require(url.protocol.equals("https", ignoreCase = true) && url.host.isNotBlank() && url.userInfo.isNullOrBlank()) {
                "يُسمح بتنزيل النماذج من اتصال HTTPS فقط."
            }
            val treeUri = getStoredTreeUri() ?: throw IOException("اختر مجلد المكتبة المشتركة أولاً.")
            val fileName = safeGGUFName(fileNameValue)
            val expected = expectedSize.toLong()
            require(expected == 0L || expected in MIN_SIZE_BYTES..MAX_SIZE_BYTES) {
                "حجم النموذج المتوقع غير صالح."
            }
            val job = DownloadJob(downloadId)
            synchronized(downloads) {
                if (downloads.isNotEmpty()) throw IOException("يوجد تنزيل نموذج مشترك قيد التنفيذ بالفعل.")
                require(downloads.putIfAbsent(downloadId, job) == null) { "معرّف التنزيل مستخدم بالفعل." }
                registeredJob = job
            }
            Thread({ downloadToSharedFolder(job, treeUri, url, fileName, expected) }, "gguf-${downloadId.take(8)}").start()
            promise.resolve(true)
        } catch (error: Exception) {
            registeredJob?.let { downloads.remove(downloadId, it) }
            promise.reject("ERR_GGUF_DOWNLOAD", error.message ?: "تعذّر بدء تنزيل النموذج إلى المكتبة المشتركة.", error)
        }
    }

    @ReactMethod
    fun cancelSharedDownload(downloadId: String, promise: Promise) {
        val job = downloads[downloadId]
        if (job == null) {
            promise.resolve(false)
            return
        }
        job.cancelled.set(true)
        job.connection?.disconnect()
        promise.resolve(true)
    }

    private fun downloadToSharedFolder(
        job: DownloadJob,
        treeUri: Uri,
        url: URL,
        fileName: String,
        expectedSize: Long
    ) {
        var documentUri: Uri? = null
        var connection: HttpURLConnection? = null
        try {
            checkCancelled(job)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
            ensureNoDuplicateName(treeUri, treeDocumentId, fileName)
            val destinationUri = DocumentsContract.createDocument(
                reactContext.contentResolver,
                parentUri,
                "application/octet-stream",
                fileName
            ) ?: throw IOException("تعذّر إنشاء الملف داخل المجلد المختار. اختر مجلداً محلياً قابلاً للكتابة.")
            documentUri = destinationUri

            val http = (url.openConnection() as? HttpURLConnection)
                ?: throw IOException("تعذّر إنشاء اتصال تنزيل HTTPS.")
            connection = http
            job.connection = http
            http.instanceFollowRedirects = true
            http.connectTimeout = 20_000
            http.readTimeout = 60_000
            http.setRequestProperty("User-Agent", "Shbeeb-One-Android/0.11")
            val responseCode = http.responseCode
            if (responseCode !in 200..299) throw IOException("رفض مصدر النموذج التنزيل (HTTP $responseCode). تحقق من رابط الملف أو شروطه.")

            val totalHint = if (expectedSize > 0L) expectedSize else http.contentLengthLong.coerceAtLeast(0L)
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastProgressAt = 0L
            val input = http.inputStream
            val output = reactContext.contentResolver.openOutputStream(destinationUri, "w")
                ?: throw IOException("تعذّر الكتابة إلى المجلد المشترك.")
            input.use { source ->
                output.use { sink ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        checkCancelled(job)
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (copied + read > MAX_SIZE_BYTES || (expectedSize > 0L && copied + read > expectedSize)) {
                            throw IOException("حجم التنزيل تجاوز الحجم المتوقع أو الحد الأقصى المسموح.")
                        }
                        sink.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 300L) {
                            emitProgress(job.id, copied, totalHint)
                            lastProgressAt = now
                        }
                    }
                    sink.flush()
                }
            }
            if (copied < MIN_SIZE_BYTES) throw IOException("الملف الناتج أصغر من الحد الأدنى لملف GGUF.")
            if (expectedSize > 0L && copied != expectedSize) {
                throw IOException("لم يكتمل الملف كما هو متوقع (تلقى التطبيق $copied من $expectedSize بايت). أُزيل الملف الجزئي.")
            }
            verifyDownloadedGGUF(destinationUri, copied)
            checkCancelled(job)
            emitEvent(EVENT_COMPLETE, Arguments.createMap().apply {
                putString("downloadId", job.id)
                putString("uri", destinationUri.toString())
                putString("name", fileName)
                putDouble("sizeBytes", copied.toDouble())
                putString("sha256", digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') })
            })
        } catch (error: Exception) {
            val partialRemoved = documentUri?.let { uri ->
                runCatching { DocumentsContract.deleteDocument(reactContext.contentResolver, uri) }.getOrDefault(false)
            } ?: true
            val message = when {
                job.cancelled.get() && partialRemoved -> "أُلغي تنزيل النموذج وحُذف الملف الجزئي."
                job.cancelled.get() -> "أُلغي التنزيل، لكن تعذّر حذف الملف الجزئي من مزود التخزين. راجع المجلد واحذفه يدوياً."
                !partialRemoved && documentUri != null -> "${error.message ?: "فشل تنزيل النموذج."} تعذّر حذف الملف الجزئي؛ راجع المجلد واحذفه يدوياً."
                else -> error.message ?: "فشل تنزيل النموذج."
            }
            Log.w(TAG, "Shared GGUF download failed", error)
            emitEvent(EVENT_ERROR, Arguments.createMap().apply {
                putString("downloadId", job.id)
                putString("message", message)
            })
        } finally {
            connection?.disconnect()
            job.connection = null
            downloads.remove(job.id)
        }
    }

    private fun checkCancelled(job: DownloadJob) {
        if (job.cancelled.get()) throw IOException("أُلغي تنزيل النموذج.")
    }

    private fun verifyDownloadedGGUF(uri: Uri, expectedBytes: Long) {
        val descriptor = reactContext.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("تعذّر إعادة فتح النموذج بعد التنزيل.")
        descriptor.use {
            require(it.statSize == expectedBytes) { "حجم الملف المحفوظ لا يطابق عدد البايتات التي نُزّلت." }
            val magic = ByteArray(4)
            FileInputStream("/proc/self/fd/${it.fd}").use { input ->
                val count = input.read(magic)
                require(count == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                    "استجابة الخادم ليست ملف GGUF صالحاً؛ أُزيل الملف الجزئي."
                }
            }
        }
    }

    private fun ensureNoDuplicateName(treeUri: Uri, treeDocumentId: String, fileName: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        reactContext.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (nameIndex >= 0) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(fileName, ignoreCase = true)) {
                        throw IOException("يوجد ملف بهذا الاسم في المجلد. اختره من المكتبة بدلاً من تنزيل نسخة ثانية.")
                    }
                }
            }
        }
    }

    private fun getStoredTreeUri(): Uri? {
        val value = reactContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE_URI_KEY, null) ?: return null
        val uri = Uri.parse(value)
        val permission = reactContext.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri && it.isReadPermission && it.isWritePermission }
        if (permission == null) {
            reactContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(TREE_URI_KEY).apply()
            return null
        }
        return uri
    }

    private fun safeGGUFName(value: String): String {
        val name = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(180)
        require(name.endsWith(".gguf", ignoreCase = true)) { "اسم ملف النموذج يجب أن ينتهي بـ .gguf." }
        require(name != ".gguf") { "اسم ملف النموذج غير صالح." }
        return name
    }

    private fun emitProgress(downloadId: String, copied: Long, total: Long) {
        emitEvent(EVENT_PROGRESS, Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putDouble("bytesWritten", copied.toDouble())
            putDouble("totalBytes", total.toDouble())
        })
    }

    private fun emitEvent(name: String, data: com.facebook.react.bridge.WritableMap) {
        if (!reactContext.hasActiveReactInstance()) return
        reactContext.runOnUiQueueThread {
            if (reactContext.hasActiveReactInstance()) {
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(name, data)
            }
        }
    }

    override fun onNewIntent(intent: Intent) = Unit

    @ReactMethod
    fun addListener(eventName: String) = Unit

    @ReactMethod
    fun removeListeners(count: Int) = Unit

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        reactContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) name = cursor.getString(column) ?: name
                }
            }
        return name
    }

    override fun invalidate() {
        pickerPromise?.reject("ERR_GGUF_PICKER", "أُغلقت شاشة التطبيق أثناء اختيار النموذج.")
        pickerPromise = null
        folderPickerPromise?.reject("ERR_GGUF_FOLDER", "أُغلقت شاشة التطبيق أثناء اختيار المجلد.")
        folderPickerPromise = null
        downloads.values.forEach { job ->
            job.cancelled.set(true)
            job.connection?.disconnect()
        }
        openDescriptors.values.forEach { runCatching { it.close() } }
        openDescriptors.clear()
        super.invalidate()
    }
}
