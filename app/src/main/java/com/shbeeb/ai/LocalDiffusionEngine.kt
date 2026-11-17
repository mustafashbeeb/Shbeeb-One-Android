package com.shbeeb.ai

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/** Owns the native model context and its seekable SAF descriptor. */
internal object LocalDiffusionEngine {
    private var modelUri: Uri? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var nativeHandle: Long = 0
    private var generating = false
    private var releasePending = false

    init {
        System.loadLibrary("shbeeb_media")
    }

    @Synchronized
    fun loadModel(context: Context, uri: Uri) {
        check(!generating) { "انتظر انتهاء التوليد قبل تبديل النموذج." }
        if (modelUri == uri && descriptor?.fileDescriptor?.valid() == true && nativeHandle != 0L) return
        releaseLocked()
        val opened = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("تعذّر فتح ملف النموذج. اختر ملفاً محلياً قابلاً للقراءة.")
        if (opened.statSize != LocalImageModelManager.MODEL_SIZE_BYTES) {
            runCatching { opened.close() }
            throw IllegalArgumentException("حجم ملف النموذج لا يطابق Stable Diffusion 1.5 Q4 الرسمي.")
        }
        try {
            val handle = nativeCreate(opened.fd)
            require(handle != 0L) { "تعذّر تحميل نموذج الصور." }
            descriptor = opened
            modelUri = uri
            nativeHandle = handle
        } catch (failure: Throwable) {
            runCatching { opened.close() }
            throw failure
        }
    }

    fun generate(
        prompt: String,
        onProgress: (Long, Long) -> Unit
    ): ByteArray {
        require(prompt.isNotBlank()) { "اكتب وصفاً للصورة أولاً." }
        val handle = synchronized(this) {
            require(nativeHandle != 0L) { "اختر نموذج الصور المحلي أولاً." }
            check(!generating) { "هناك عملية توليد أخرى قيد التنفيذ." }
            generating = true
            nativeHandle
        }
        try {
            val listener = object : NativeProgressListener {
                override fun onProgress(completed: Long, total: Long) = onProgress(completed, total)
            }
            return nativeGenerate(
                handle,
                prompt.trim(),
                "blurry, low quality, bad anatomy, extra fingers, malformed hands, watermark, random text",
                512,
                512,
                20,
                -1L,
                listener
            ) ?: throw IllegalStateException("لم يُنتج المحرك صورة.")
        } finally {
            synchronized(this) {
                generating = false
                if (releasePending) releaseLocked()
            }
        }
    }

    fun cancel() {
        val handle = synchronized(this) { nativeHandle }
        if (handle != 0L) nativeCancel(handle)
    }

    fun release() {
        synchronized(this) {
            if (generating && nativeHandle != 0L) {
                releasePending = true
                nativeCancel(nativeHandle)
            } else {
                releaseLocked()
            }
        }
    }

    private fun releaseLocked() {
        if (nativeHandle != 0L) nativeDestroy(nativeHandle)
        nativeHandle = 0
        modelUri = null
        releasePending = false
        runCatching { descriptor?.close() }
        descriptor = null
    }

    private fun interface NativeProgressListener {
        fun onProgress(completed: Long, total: Long)
    }

    private external fun nativeCreate(fileDescriptor: Int): Long
    private external fun nativeGenerate(
        nativeHandle: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        progressListener: NativeProgressListener
    ): ByteArray?
    private external fun nativeCancel(nativeHandle: Long)
    private external fun nativeDestroy(nativeHandle: Long)
}
