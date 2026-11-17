package com.shbeeb.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/** A small true offline raster filter tool, not an AI image model. */
internal object OfflineImageFilters {
    fun edit(original: Bitmap, effect: String): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when (effect) {
            "bw" -> paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
            "sepia" -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }
        Canvas(result).drawBitmap(original, 0f, 0f, paint)
        return result
    }
}

@Composable
internal fun OfflineImageEditor() {
    val context = LocalContext.current
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("original") }
    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val resolver = context.contentResolver
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("تعذّر فتح الملف.")
                require(options.outWidth > 0 && options.outHeight > 0) {
                    "تعذّرت قراءة أبعاد الصورة."
                }
                var sample = 1
                while (maxOf(options.outWidth / sample, options.outHeight / sample) > 1600) {
                    sample *= 2
                }
                val imageOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, imageOptions)
                } ?: error("هذا الملف ليس صورة قابلة للمعاينة.")
                original = decoded
                edited = decoded
                filter = "original"
                message = "تم فتح الصورة محلياً بدقة مناسبة لتقليل استهلاك الذاكرة."
            }.onFailure {
                message = it.message ?: "تعذّرت معالجة الصورة."
            }
        }
    }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bitmap = edited
        if (uri != null && bitmap != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        "تعذّر إنشاء ملف PNG."
                    }
                } ?: error("تعذّر فتح الملف للحفظ.")
                message = "تم حفظ الصورة المعدّلة بنجاح."
            }.onFailure { message = "فشل حفظ الصورة؛ جرّب موقعاً آخر." }
        }
    }
    Surface(
        color = Color(0xFF26183F),
        shape = RoundedCornerShape(19.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تعديل صورة بدون إنترنت", color = Color(0xFFF7CB78))
            Text("اختر صورة من جهازك، طبّق فلتر أبيض وأسود أو سيبيا، ثم احفظ النسخة. هذا تعديل عادي وليس توليداً بالذكاء.",
                color = Color(0xFFB7C8D8))
            Button(onClick = { chooser.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("اختيار صورة من الهاتف")
            }
            if (edited != null) {
                val shown = remember(edited) { edited?.asImageBitmap() }
                if (shown != null) {
                    Image(
                        bitmap = shown, contentDescription = "معاينة الصورة المعدلة",
                        modifier = Modifier.fillMaxWidth().aspectRatio(
                            shown.width.toFloat() / shown.height.toFloat()
                        ), contentScale = ContentScale.Fit
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        "original" to "الأصلية",
                        "bw" to "أبيض وأسود",
                        "sepia" to "سيبيا"
                    ).forEach { (id, label) ->
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val source = original
                                if (source != null) {
                                    filter = id
                                    edited = if (id == "original") source
                                        else OfflineImageFilters.edit(source, id)
                                }
                            }
                        ) { Text(label) }
                    }
                }
                Button(
                    onClick = { save.launch("shbeeb-edited.png") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حفظ الصورة المعدلة") }
            }
            if (message.isNotBlank()) {
                Text(message, color = Color(0xFFB7C8D8))
            }
        }
    }
}
