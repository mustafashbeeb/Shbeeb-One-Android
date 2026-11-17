package com.shbeeb.ai

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.OutputStreamWriter

/** Diagnostic facts are local to the device, with no network/API calls. */
internal data class DeviceCheck(
    val androidApi: Int,
    val device: String,
    val memoryTotalMb: Long,
    val memoryFreeMb: Long,
    val storageFreeMb: Long,
    val importedModelMb: Long?,
    val supportsVideoEncoder: Boolean
) {
    fun toArabicReport(): String = buildString {
        appendLine("تقرير شبيب ون — فحص الجهاز")
        appendLine("إصدار التطبيق: 0.10.3 تجريبي")
        appendLine("نسخة Android API: $androidApi")
        appendLine("الجهاز: $device")
        appendLine("RAM الكلية التقريبية: $memoryTotalMb ميغابايت")
        appendLine("RAM المتاحة التقريبية: $memoryFreeMb ميغابايت")
        appendLine("المساحة المتاحة للتطبيق: $storageFreeMb ميغابايت")
        appendLine(
            if (importedModelMb == null) "نموذج الهاتف: غير مستورد"
            else "نموذج الهاتف: ملف مستورد حجمه $importedModelMb ميغابايت (لم يُختبر تشغيله)"
        )
        appendLine(
            if (supportsVideoEncoder) "ترميز H.264: يوجد مشفّر يدعم الإعدادات المطلوبة مبدئياً"
            else "ترميز H.264: لم نعثر على مشفّر يدعم الإعدادات المطلوبة"
        )
        appendLine("المحادثة المحلية: لم تُفحص بالاستدلال الفعلي في هذا التقرير")
        appendLine("المحادثات السحابية: لم تُفحص؛ ولا تم إرسال أي طلب API")
        appendLine("الصور والفيديو والصوت: تحتاج اختباراً عملياً على الهاتف")
        appendLine("لا يحتوي هذا التقرير على مفاتيح API أو رسائل أو ملفات المستخدم")
    }
}

internal object DeviceDiagnosticScanner {
    fun scan(context: Context): DeviceCheck {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val space = StatFs(context.filesDir.absolutePath)
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            LocalVideoMaker.WIDTH,
            LocalVideoMaker.HEIGHT
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 2_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, LocalVideoMaker.FPS)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        }
        val hasEncoder = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(format) != null
        val modelSizeBytes = LocalModelFiles.info(context)?.sizeBytes
        return DeviceCheck(
            androidApi = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".take(120),
            memoryTotalMb = memory.totalMem / 1_048_576L,
            memoryFreeMb = memory.availMem / 1_048_576L,
            storageFreeMb = space.availableBytes / 1_048_576L,
            importedModelMb = modelSizeBytes?.div(1_048_576L),
            supportsVideoEncoder = hasEncoder
        )
    }
}

/** Compact field rows reduce RTL wrapping on narrow Android screens. */
@Composable
private fun DiagnosticRow(label: String, value: String, attention: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label, color = Color(0xFFD5C7EA), fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = if (attention) Color(0xFFF7CB78) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
internal fun DeviceDiagnosticsScreen() {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var busy by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<DeviceCheck?>(null) }
    var status by remember { mutableStateOf("") }
    val report = check?.toArabicReport().orEmpty()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && report.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        writer.write(report)
                    }
                } ?: error("تعذّر فتح الملف.")
                status = "تم حفظ التقرير في الموقع الذي اخترته."
            }.onFailure { status = "تعذّر حفظ التقرير." }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            horizontal = 16.dp, vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "فحص الجهاز",
            fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF7CB78)
        )
        Text(
            "نتائج من الجهاز مباشرة، دون اتصال بالإنترنت أو إرسال أي معلومات.",
            color = Color(0xFFB7C8D8), fontSize = 12.sp
        )
        Button(
            onClick = {
                busy = true
                status = "جارٍ فحص مواصفات الجهاز..."
                Thread {
                    val result = runCatching { DeviceDiagnosticScanner.scan(context) }
                    handler.post {
                        result.onSuccess {
                            check = it
                            status = "اكتمل فحص مواصفات الجهاز."
                        }.onFailure {
                            status = "تعذّر قراءة بعض مواصفات الجهاز."
                        }
                        busy = false
                    }
                }.start()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "جارٍ الفحص..." else "إعادة فحص الجهاز") }
        if (status.isNotBlank()) Text(status, color = Color(0xFFB7C8D8), fontSize = 12.sp)
        val item = check
        if (item != null) {
            Surface(
                color = Color(0xFF281845),
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "المواصفات", color = Color(0xFFF7CB78),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    DiagnosticRow("الجهاز", item.device)
                    DiagnosticRow("أندرويد", "API ${item.androidApi}")
                    DiagnosticRow("RAM الكلية", "${item.memoryTotalMb} MB")
                    DiagnosticRow("RAM المتاحة الآن", "${item.memoryFreeMb} MB")
                    DiagnosticRow("المساحة المتاحة", "${item.storageFreeMb} MB")
                    DiagnosticRow(
                        "نموذج الهاتف",
                        item.importedModelMb?.let { "مستورد ${it} MB — غير مختبر" }
                            ?: "غير مثبت",
                        attention = item.importedModelMb == null
                    )
                    DiagnosticRow(
                        "مشفّر فيديو H.264",
                        if (item.supportsVideoEncoder) "مدعوم مبدئياً" else "غير مؤكد",
                        attention = !item.supportsVideoEncoder
                    )
                }
            }
            Surface(
                color = Color(0xFF23163A),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text("ما الذي لم يُفحص بعد؟", color = Color(0xFFF7CB78),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ردود المحركات، تشغيل النموذج المحلي، الإملاء والصوت، " +
                            "إنشاء الفيديو والصور. دعم H.264 وحده لا يثبت نجاح إنشاء ملف MP4.",
                        color = Color.White, fontSize = 12.sp
                    )
                }
            }
            OutlinedButton(
                onClick = { export.launch("shbeeb-one-device-check.txt") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ تقرير الفحص النصي") }
        }
    }
}
