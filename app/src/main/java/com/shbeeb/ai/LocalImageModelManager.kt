package com.shbeeb.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal object LocalImageModelManager {
    const val MODEL_SIZE_BYTES = 1_566_768_416L
    const val MODEL_NAME = "stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf"
    const val MODEL_URL = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/main/$MODEL_NAME?download=true"
    const val MODEL_SHA256 = "b8944e9fe0b69b36ae1b5bb0185b3a7b8ef14347fe0fa9af6c64c4829022261f"
    private const val PREFS = "shbeeb_local_image_model"
    private const val URI_KEY = "sd15_model_uri"

    fun savedUri(context: Context): Uri? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(URI_KEY, null)?.let(Uri::parse)
    }.getOrNull()

    fun rememberUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(URI_KEY, uri.toString()).apply()
    }

    fun verifySelectedFile(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("تعذّر فتح الملف المحدد.")
        descriptor.use {
            require(it.statSize == MODEL_SIZE_BYTES) {
                "حجم الملف ليس ملف SD 1.5 Q4 المتوقع (1.57 GB)."
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val signature = ByteArray(4)
                require(input.read(signature) == 4 && signature.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                    "الملف لا يحتوي ترويسة GGUF صالحة."
                }
            } ?: throw IOException("تعذّر قراءة بداية الملف.")
        }
        rememberUri(context, uri)
        return displayName(context, uri)
    }

    fun displayName(context: Context, uri: Uri): String {
        var result = MODEL_NAME
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) result = cursor.getString(column) ?: result
            }
        }
        return result
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(URI_KEY).apply()
    }

    fun download(
        context: Context,
        destination: Uri,
        canceled: AtomicBoolean,
        onProgress: (Long, Long) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Shbeeb-One-Android")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("فشل تنزيل النموذج (HTTP $status).")
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0 && declaredLength != MODEL_SIZE_BYTES) {
                throw IOException("حجم الملف من المصدر تغير؛ أُلغي التنزيل لحماية الجهاز.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        if (canceled.get()) throw InterruptedException("أُلغي تنزيل النموذج.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MODEL_SIZE_BYTES) throw IOException("الملف أكبر من الحجم المتوقع؛ أوقفنا التنزيل.")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        onProgress(downloaded, MODEL_SIZE_BYTES)
                    }
                }
            } ?: throw IOException("تعذّر الكتابة إلى الموقع الذي اخترته.")

            if (downloaded != MODEL_SIZE_BYTES) throw IOException("التنزيل غير مكتمل: وصل ${(downloaded / 1_000_000)} من 1567 MB.")
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(MODEL_SHA256, ignoreCase = true)) {
                throw IOException("فشل التحقق من SHA-256؛ لم نعتمد الملف المنزّل.")
            }
            context.contentResolver.openInputStream(destination)?.use { input ->
                val signature = ByteArray(4)
                if (input.read(signature) != 4 || !signature.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                    throw IOException("التنزيل لا يحتوي ملف GGUF صالحاً.")
                }
            } ?: throw IOException("تعذّر التحقق من الملف بعد التنزيل.")
            rememberUri(context, destination)
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        } finally {
            connection?.disconnect()
        }
    }
}
