package com.shbeeb.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun VideoMakerControls() {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    val choosePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !busy) {
            runCatching {
                val resolver = context.contentResolver
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: error("تعذّر فتح الصورة.")
                require(opts.outWidth > 0 && opts.outHeight > 0) { "الصورة غير صالحة." }
                var sample = 1
                while (maxOf(opts.outWidth / sample, opts.outHeight / sample) > 1600) sample *= 2
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                } ?: error("تعذّر قراءة الصورة.")
                photo = bitmap
                outputFile = null
                message = "تم اختيار الصورة. سيصنع التطبيق مقطعاً متحركاً بالصورة نفسها."
            }.onFailure { message = it.message ?: "تعذّر فتح الصورة." }
        }
    }
    val saveMp4 = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val file = outputFile
        if (uri != null && file != null && file.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                } ?: error("تعذّر فتح الوجهة.")
                message = "تم حفظ الفيديو MP4 في المكان الذي اخترته."
            }.onFailure { message = "تعذّر حفظ الفيديو؛ جرّب موقعاً آخر." }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp), color = Color(0xFF26183F)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("صناعة فيديو عمودي من صورة — بدون إنترنت",
                color = Color(0xFFF7CB78))
            Text(
                "مقطع صامت لمدة 8 ثوانٍ، 9:16، تقريب كاميرا ناعم من صورة تختارها. هذه معالجة فيديو حقيقية على الهاتف وليست تحريك شخصيات بالذكاء الاصطناعي.",
                color = Color(0xFFB7C8D8)
            )
            Button(onClick = { choosePhoto.launch("image/*") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("اختيار صورة للمقطع") }
            val preview = remember(photo) { photo?.asImageBitmap() }
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "الصورة المستخدمة لإنشاء فيديو",
                    modifier = Modifier.fillMaxWidth().aspectRatio(
                        preview.width.toFloat() / preview.height.toFloat()
                    ), contentScale = ContentScale.Fit
                )
            }
            Button(
                onClick = {
                    val input = photo
                    if (!busy && input != null) {
                        busy = true
                        message = "جارٍ معالجة مقطع 8 ثوانٍ محلياً. لا تغلق التطبيق أثناء المعالجة."
                        val file = File(context.cacheDir, "shbeeb-one-local-clip.mp4")
                        Thread {
                            val response = runCatching { LocalVideoMaker.render(input, file); file }
                            handler.post {
                                response.onSuccess {
                                    outputFile = it
                                    message = "اكتمل إنشاء المقطع. يمكنك حفظه بصيغة MP4."
                                }.onFailure {
                                    outputFile = null
                                    message = it.message ?: "مشفر الفيديو غير متوافق مع الجهاز."
                                }
                                busy = false
                            }
                        }.start()
                    }
                }, enabled = photo != null && !busy, modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "جارٍ تجهيز الفيديو…" else "إنشاء فيديو 8 ثوانٍ") }
            if (outputFile != null && !busy) {
                OutlinedButton(
                    onClick = { saveMp4.launch("shbeeb-one-8sec.mp4") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حفظ المقطع MP4") }
            }
            if (message.isNotBlank()) Text(message, color = Color(0xFFB7C8D8))
        }
    }
}
