package com.shbeeb.ai

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmLegacyDelete by remember { mutableStateOf(false) }
    val modelInfo = LocalModelFiles.info(context)
    val installed = LocalModelFiles.isInstalled(context)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            message = "جارٍ فحص إذن الملف وحجمه… لا يتم نسخ النموذج إلى التطبيق."
            Thread {
                val result = runCatching {
                    val selected = LocalModelFiles.select(context, uri)
                    LocalOnDeviceEngine.reset()
                    selected
                }
                handler.post {
                    result.onSuccess { selected ->
                        message = "تم ربط ${selected.displayName} (${selected.sizeBytes / 1_048_576L} MB). سيقرأه LiteRT مباشرة من موقعه؛ لا تُنشأ نسخة ثانية. اختبر رداً محلياً قبل الاعتماد عليه."
                        onInstalled()
                    }.onFailure {
                        message = it.message ?: "تعذّر استخدام الملف المحدد."
                    }
                    busy = false
                }
            }.start()
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (LocalModelFiles.selectedUri(context) != null) "فك ربط النموذج المشترك" else "حذف النسخة القديمة") },
            text = {
                Text(
                    if (LocalModelFiles.selectedUri(context) != null)
                        "سيُزال إذن Shbeeb One عن الملف فقط؛ لن يُحذف ملفك من المجلد المشترك."
                    else
                        "سيُحذف ملف النموذج القديم من مساحة التطبيق الخاصة. لا يمكن استرجاعه إلا بإعادة اختياره أو تنزيله."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    busy = true
                    Thread {
                        val result = runCatching {
                            LocalOnDeviceEngine.reset()
                            if (LocalModelFiles.selectedUri(context) != null) {
                                LocalModelFiles.clearReference(context)
                                "تم فك ربط الملف؛ بقي محفوظاً في المجلد الذي اخترته."
                            } else {
                                check(LocalModelFiles.deleteLegacyCopy(context)) { "تعذّر حذف النسخة القديمة." }
                                "تم حذف النسخة القديمة من مساحة التطبيق."
                            }
                        }
                        handler.post {
                            message = result.getOrElse { it.message ?: "تعذّر إكمال العملية." }
                            onInstalled()
                            busy = false
                        }
                    }.start()
                }) { Text(if (LocalModelFiles.selectedUri(context) != null) "فك الربط" else "حذف النسخة") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("إلغاء") } }
        )
    }

    if (confirmLegacyDelete) {
        AlertDialog(
            onDismissRequest = { confirmLegacyDelete = false },
            title = { Text("حذف النسخة القديمة لتوفير المساحة") },
            text = { Text("هذه نسخة سابقة داخل مساحة Shbeeb One الخاصة. سيبقى ملف النموذج المشترك المحدد كما هو. لا يمكن التراجع عن حذف النسخة القديمة.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLegacyDelete = false
                    busy = true
                    Thread {
                        val deleted = runCatching {
                            LocalOnDeviceEngine.reset()
                            LocalModelFiles.deleteLegacyCopy(context)
                        }.getOrDefault(false)
                        handler.post {
                            message = if (deleted) "حُذفت النسخة القديمة؛ لم يُحذف الملف المشترك."
                                else "تعذّر حذف النسخة القديمة."
                            onInstalled()
                            busy = false
                        }
                    }.start()
                }) { Text("حذف النسخة القديمة") }
            },
            dismissButton = { TextButton(onClick = { confirmLegacyDelete = false }) { Text("إلغاء") } }
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
                when {
                    modelInfo?.isShared == true ->
                        "الملف المشترك: ${modelInfo.displayName} — ${modelInfo.sizeBytes / 1_048_576L} MB. يُفتح مباشرة من موقع المستخدم دون نسخه إلى مساحة التطبيق."
                    modelInfo != null ->
                        "نموذج قديم: ${modelInfo.displayName} — ${modelInfo.sizeBytes / 1_048_576L} MB داخل مساحة التطبيق الخاصة؛ التطبيقات الأخرى لا تصل إليه."
                    else ->
                        "لا يوجد نموذج محدد. اختر ملف .litertlm محفوظاً على الجهاز. لا ينسخ Shbeeb One الملف، ويجب أن يكون موفر الملفات محلياً وقابلاً للبحث."
                },
                color = Color.White
            )
            Text(
                "التوافق مهم: هذا المحرك يقبل .litertlm فقط. نموذج محادثة GGUF يحتاج محرك llama.cpp، وGGUF الخاص بالصور ليس نموذج محادثة. يحتاج الملف إلى ترخيص مناسب؛ نجاح اختياره لا يضمن توافقه حتى تنجح محادثة تجريبية.",
                color = Color(0xFFB7C8D8)
            )
            Text(
                "لا يأتي نموذج داخل APK. التشغيل CPU فقط. وضع الرد السريع يحدد طول الإجابة ولا يزيد معرفة النموذج؛ النماذج الصغيرة قد تخطئ.",
                color = Color(0xFFB7C8D8)
            )
            Text("طول إجابة ذكاء الهاتف", color = Color(0xFF35D6FF))
            LocalResponseMode.values().forEach { mode ->
                OutlinedButton(
                    onClick = { onResponseModeChanged(mode) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text((if (responseMode == mode) "✓ " else "") + mode.title)
                }
            }
            if (installed) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        message = "جارٍ إغلاق سياق النموذج…"
                        Thread {
                            val result = runCatching { LocalOnDeviceEngine.reset() }
                            handler.post {
                                message = if (result.isSuccess)
                                    "بدأت جلسة استدلال جديدة. بقي ملف النموذج كما هو."
                                else "تعذرت إعادة تهيئة النموذج."
                                busy = false
                            }
                        }.start()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بدء جلسة ذكاء محلي جديدة") }
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community")))
                }.onFailure { message = "تعذّر فتح صفحة النماذج. استخدم المتصفح." }
            }) { Text("استعراض نماذج LiteRT المرخّصة") }
            Text(
                "للتجربة، يعرض مجتمع LiteRT نماذج Gemma بصيغة .litertlm. اقرأ بطاقة النموذج وترخيصه ومتطلباته، ثم اختر الملف المحفوظ محلياً.",
                color = Color(0xFFB7C8D8)
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main")))
                }.onFailure { message = "تعذّر فتح صفحة النموذج، افتح Hugging Face من المتصفح." }
            }) { Text("صفحة نموذج Gemma 3 1B للتجربة") }
            Button(
                onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "جارٍ الفحص…" else "اختيار ملف .litertlm من المكتبة المشتركة") }
            if (installed) {
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (LocalModelFiles.selectedUri(context) != null) "فك ربط الملف (لا يحذفه)" else "حذف النسخة القديمة") }
            }
            if (LocalModelFiles.selectedUri(context) != null && LocalModelFiles.hasLegacyModel(context)) {
                OutlinedButton(
                    onClick = { confirmLegacyDelete = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حذف النسخة القديمة لتوفير مساحة") }
            }
            if (message.isNotBlank()) Text(message, color = Color(0xFFB7C8D8))
        }
    }
}
