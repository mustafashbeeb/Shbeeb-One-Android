package com.shbeeb.ai

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun LocalModelSettings(
    onInstalled: () -> Unit,
    responseMode: LocalResponseMode,
    onResponseModeChanged: (LocalResponseMode) -> Unit
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var copying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val installed = LocalModelFiles.isInstalled(context)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !copying) {
            copying = true
            message = "جارٍ نسخ النموذج إلى مساحة التطبيق. قد يستغرق ذلك عدة دقائق."
            Thread {
                val response = runCatching {
                    val size = LocalModelFiles.import(context, uri)
                    LocalOnDeviceEngine.reset()
                    size
                }
                handler.post {
                    response.onSuccess { size ->
                        message = "تم استيراد نموذج محلي: ${size / (1024 * 1024)} ميغابايت. اختبر الرد من شاشة المساعد."
                        onInstalled()
                    }.onFailure {
                        message = it.message ?: "فشل استيراد النموذج. جرّب ملفاً آخر."
                    }
                    copying = false
                }
            }.start()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نموذج الذكاء المحلي") },
            text = { Text("هل تريد حذف ملف النموذج الكبير من مساحة الهاتف؟ سيتم فقد إمكانية المحادثة دون إنترنت إلى أن تعيد استيراده.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    copying = true
                    Thread {
                        val result = runCatching {
                            LocalOnDeviceEngine.reset()
                            val model = LocalModelFiles.modelFile(context)
                            !model.exists() || model.delete()
                        }
                        handler.post {
                            message = if (result.getOrDefault(false))
                                "تم حذف النموذج المحلي من مساحة التطبيق."
                            else "تعذّر حذف النموذج؛ حاول لاحقاً."
                            onInstalled()
                            copying = false
                        }
                    }.start()
                }) { Text("حذف النموذج") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }

    Surface(
        color = Color(0xFF0D1B2A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("محرك ذكاء داخل الهاتف — تجريبي", color = Color(0xFF35D6FF))
            Text(
                if (installed)
                    "استُورد ملف بحجم ${LocalModelFiles.modelFile(context).length() / (1024 * 1024)} ميغابايت؛ توافقه غير مؤكد حتى ينجح رد تجريبي. لا يحتاج المحرك مفتاح API أو إنترنت بعد الاستيراد."
                else
                    "لم يُثبّت نموذج على الهاتف. نزّل نموذج .litertlm صغيراً ومرخّصاً من LiteRT Community، ثم استورده هنا. ملف GGUF لا يعمل في هذا المحرك.",
                color = Color.White
            )
            Text(
                "مهم: النموذج لا يأتي داخل APK. التشغيل CPU فقط. وضع الرد السريع يقلل طول الإجابة لكنه لا يجعل النموذج نفسه أذكى. نموذج 1B قد يخطئ بالمعلومات الأساسية.",
                color = Color(0xFFB7C8D8)
            )
            Text("طول إجابة ذكاء الهاتف", color = Color(0xFF35D6FF))
            LocalResponseMode.values().forEach { mode ->
                OutlinedButton(
                    onClick = { onResponseModeChanged(mode) },
                    enabled = !copying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text((if (responseMode == mode) "✓ " else "") + mode.title)
                }
            }
            if (installed) {
                OutlinedButton(
                    onClick = {
                        copying = true
                        message = "جارٍ إعادة تهيئة سياق النموذج..."
                        Thread {
                            val result = runCatching { LocalOnDeviceEngine.reset() }
                            handler.post {
                                message = if (result.isSuccess)
                                    "بدأت جلسة استدلال جديدة. لا يحذف هذا سجل المحادثة المحفوظ في التطبيق."
                                else "لم يتمكن النموذج من إعادة التهيئة."
                                copying = false
                            }
                        }.start()
                    },
                    enabled = !copying,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بدء جلسة ذكاء محلي جديدة") }
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://huggingface.co/litert-community")))
                }.onFailure { message = "تعذّر فتح صفحة النماذج. استخدم المتصفح." }
            }) { Text("استعراض نماذج LiteRT المرخّصة") }
            Text(
                "للبدء: يوجد نموذج Gemma 3 1B مضغوط بصيغة .litertlm (نحو 584 ميغابايت). يُحمّل من المصدر بعد قبول شروط ترخيص Gemma. وجود الملف لا يضمن تشغيله؛ جرّب دون إنترنت.",
                color = Color(0xFFB7C8D8)
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main")))
                }.onFailure { message = "تعذّر فتح صفحة النموذج، افتح Hugging Face من المتصفح." }
            }) { Text("صفحة نموذج Gemma 3 1B المناسب للتجربة") }
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !copying,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (copying) "جارٍ الاستيراد…" else "استيراد نموذج .litertlm من الملفات") }
            if (installed) {
                OutlinedButton(
                    onClick = { confirmDelete = true }, enabled = !copying,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حذف النموذج من الهاتف") }
            }
            if (message.isNotBlank()) Text(message, color = Color(0xFFB7C8D8))
        }
    }
}
