package com.shbeeb.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/** Eight-second silent MP4 from a selected photograph, with slow Ken Burns zoom.
 * This is ordinary on-device video processing, NOT AI video synthesis.
 */
internal object LocalVideoMaker {
    internal const val WIDTH = 576
    internal const val HEIGHT = 1024
    internal const val FPS = 12
    internal const val SECONDS = 8

    fun render(bitmap: Bitmap, destination: File) {
        require(bitmap.width > 0 && bitmap.height > 0) { "الصورة الأصلية غير صالحة." }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_200_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var encoderStarted = false
        var muxerStarted = false
        var track = -1
        var ended = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        val colors = IntArray(WIDTH * HEIGHT)

        fun drain(waitForEos: Boolean) {
            var attempts = 0
            while (!ended) {
                val index = encoder.dequeueOutputBuffer(
                    bufferInfo, if (waitForEos) 30_000 else 0
                )
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!waitForEos) return
                        if (++attempts > 200) error("انتهت مهلة ترميز الفيديو.")
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        require(!muxerStarted) { "تغيّرت صيغة الفيديو أكثر من مرة." }
                        track = muxer!!.addTrack(encoder.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val encoded = encoder.getOutputBuffer(index)
                        if (bufferInfo.size > 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            require(muxerStarted && encoded != null) { "فشل تهيئة ملف الفيديو." }
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(track, encoded, bufferInfo)
                        }
                        ended = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(index, false)
                        if (!waitForEos && index < 0) return
                    }
                }
            }
        }

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true
            muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val count = FPS * SECONDS
            for (frame in 0 until count) {
                val zoom = 1f + 0.09f * frame / (count - 1).toFloat()
                val sourceAspect = bitmap.width.toFloat() / bitmap.height
                val outputAspect = WIDTH.toFloat() / HEIGHT
                val croppedW = if (sourceAspect > outputAspect)
                    (bitmap.height * outputAspect / zoom).toInt() else (bitmap.width / zoom).toInt()
                val croppedH = if (sourceAspect > outputAspect)
                    (bitmap.height / zoom).toInt() else (bitmap.width / outputAspect / zoom).toInt()
                val w = croppedW.coerceIn(1, bitmap.width)
                val h = croppedH.coerceIn(1, bitmap.height)
                val left = (bitmap.width - w) / 2
                val top = (bitmap.height - h) / 2
                canvas.drawBitmap(
                    bitmap, Rect(left, top, left + w, top + h),
                    Rect(0, 0, WIDTH, HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG)
                )
                frameBitmap.getPixels(colors, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                drain(false)
                var inputIndex = encoder.dequeueInputBuffer(2_000_000)
                if (inputIndex < 0) {
                    drain(false)
                    inputIndex = encoder.dequeueInputBuffer(2_000_000)
                }
                require(inputIndex >= 0) { "المشفر غير جاهز لاستقبال إطار الفيديو." }
                val image = encoder.getInputImage(inputIndex)
                    ?: error("مشفّر الفيديو لا يدعم إدخال الصور YUV.")
                val planes = image.planes
                val yp = planes[0]
                val up = planes[1]
                val vp = planes[2]
                for (y in 0 until HEIGHT) {
                    for (x in 0 until WIDTH) {
                        val pixel = colors[y * WIDTH + x]
                        val red = pixel shr 16 and 255
                        val green = pixel shr 8 and 255
                        val blue = pixel and 255
                        val luminance = (((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16)
                            .coerceIn(0, 255)
                        yp.buffer.put(y * yp.rowStride + x * yp.pixelStride, luminance.toByte())
                        if (x % 2 == 0 && y % 2 == 0) {
                            val u = (((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128)
                                .coerceIn(0, 255)
                            val v = (((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128)
                                .coerceIn(0, 255)
                            val ux = x / 2
                            val uy = y / 2
                            up.buffer.put(uy * up.rowStride + ux * up.pixelStride, u.toByte())
                            vp.buffer.put(uy * vp.rowStride + ux * vp.pixelStride, v.toByte())
                        }
                    }
                }
                encoder.queueInputBuffer(
                    inputIndex, 0, WIDTH * HEIGHT * 3 / 2,
                    frame * 1_000_000L / FPS, 0
                )
                drain(false)
            }
            var eos = encoder.dequeueInputBuffer(2_000_000)
            if (eos < 0) {
                drain(false)
                eos = encoder.dequeueInputBuffer(2_000_000)
            }
            require(eos >= 0) { "تعذّر إنهاء ترميز الفيديو." }
            encoder.queueInputBuffer(eos, 0, 0, SECONDS * 1_000_000L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            require(muxerStarted && destination.length() > 1024L) {
                "لم يكتمل ملف الفيديو."
            }
        } finally {
            frameBitmap.recycle()
            if (encoderStarted) runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!destination.exists() || destination.length() < 1024) {
                destination.delete()
            }
        }
    }
}
