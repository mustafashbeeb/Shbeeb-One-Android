package com.shbeeb.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

private val ToolSurface = Color(0xFF0D1B2A)
private val ToolText = Color(0xFFB7C8D8)
private val ToolGold = Color(0xFF35D6FF)

/** Tasks are created, marked and deleted explicitly; no silent automation. */
@Composable
internal fun TasksScreen() {
    val context = LocalContext.current
    val store = remember(context) { TaskStore(context) }
    val tasks = remember(context) {
        mutableStateListOf<SimpleTask>().apply { addAll(store.load()) }
    }
    var draft by remember { mutableStateOf("") }
    var deletePending by remember { mutableStateOf<Long?>(null) }

    if (deletePending != null) {
        AlertDialog(
            onDismissRequest = { deletePending = null },
            title = { Text("تأكيد حذف المهمة") },
            text = { Text("سيتم حذف هذه المهمة من جهازك، ولا يمكن استرجاعها.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deletePending
                    tasks.removeAll { it.id == id }
                    store.save(tasks)
                    deletePending = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deletePending = null }) { Text("إلغاء") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المهام", fontSize = 25.sp, color = ToolGold, fontWeight = FontWeight.Bold)
        Text("قائمة عملية محفوظة على الهاتف. لا توجد مهام خلفية أو تنبيهات آلية حالياً.",
            color = ToolText, fontSize = 13.sp)
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 200) draft = it },
            label = { Text("اكتب مهمة جديدة") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (draft.isNotBlank() && tasks.size < 100) {
                    tasks.add(SimpleTask(System.nanoTime(), draft.trim(), false))
                    store.save(tasks)
                    draft = ""
                }
            },
            enabled = draft.isNotBlank() && tasks.size < 100,
            modifier = Modifier.fillMaxWidth()
        ) { Text("إضافة المهمة") }

        if (tasks.isEmpty()) {
            Text("ما في مهام بعد. أضف أول مهمة من الخانة فوق.", color = ToolText)
        }
        tasks.forEach { task ->
            Surface(color = ToolSurface, shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(
                        checked = task.complete,
                        onCheckedChange = { done ->
                            val index = tasks.indexOfFirst { it.id == task.id }
                            if (index >= 0) {
                                tasks[index] = task.copy(complete = done)
                                store.save(tasks)
                            }
                        }
                    )
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                        color = if (task.complete) ToolText.copy(alpha = .6f) else Color.White
                    )
                    TextButton(onClick = { deletePending = task.id }) {
                        Text("حذف", color = Color(0xFFFFA99D))
                    }
                }
            }
        }
    }
}

/** Read only a short bounded text excerpt from user-selected content URI. */
@Composable
internal fun FilesScreen() {
    val context = LocalContext.current
    var preview by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri).orEmpty()
                require(mime.startsWith("text/") ||
                    mime in setOf("application/json", "application/xml")) {
                    "هذا النوع غير مدعوم للمعاينة النصية. جرّب TXT أو JSON أو CSV أو XML."
                }
                var selected = "ملف نصي"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) selected = cursor.getString(0) ?: selected
                    }
                val output = ByteArrayOutputStream()
                resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(4096)
                    while (output.size() < 128_000) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 128_000 - output.size()))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                } ?: error("تعذّر فتح الملف المحدد.")
                name = selected
                preview = output.toString("UTF-8")
                status = "تم فتح معاينة نصية محدودة حتى 128 كيلوبايت؛ لم يتم رفع الملف إلى الإنترنت."
            }.onFailure { status = it.message ?: "تعذّرت قراءة الملف." }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ملفاتي", color = ToolGold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(
            "اختر ملفاً نصياً بنفسك. التطبيق يعرض معاينة محلية فقط ولا يرسلها لمحرك الذكاء. ملفات PDF والصور تحتاج قارئاً مستقلاً لاحقاً.",
            color = ToolText, fontSize = 13.sp
        )
        Button(
            onClick = { launcher.launch(arrayOf("text/*", "application/json", "application/xml")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("اختيار ملف للمعاينة") }
        if (status.isNotBlank()) Text(status, color = ToolText)
        if (preview.isNotBlank()) {
            Surface(color = ToolSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text(name, color = ToolGold, fontWeight = FontWeight.Bold)
                    Text(preview.take(6000), color = Color.White, fontSize = 13.sp)
                    if (preview.length > 6000) {
                        Text("المعاينة الظاهرة مختصرة إلى 6000 حرف.", color = ToolText)
                    }
                }
            }
            OutlinedButton(onClick = {
                name = ""
                preview = ""
                status = "أُغلقت المعاينة من ذاكرة التطبيق."
            }, modifier = Modifier.fillMaxWidth()) { Text("إغلاق المعاينة") }
        }
    }
}

/** An offline, deterministic prompt template — not a generative image/video engine. */
@Composable
internal fun CreativeScreen(kind: String, hasOpenAiKey: Boolean, readOpenAiKey: () -> String) {
    val context = LocalContext.current
    val video = kind == "الفيديو"
    var idea by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageError by remember { mutableStateOf("") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val saveImage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val data = imageBytes
        if (uri != null && data != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    ?: error("تعذّر فتح الملف للحفظ.")
                imageError = "تم حفظ الصورة في المكان الذي اخترته."
            }.onFailure { imageError = "فشل حفظ الصورة على الجهاز." }
        }
    }
    if (pendingImage) {
        AlertDialog(
            onDismissRequest = { pendingImage = false },
            title = { Text("تأكيد توليد صورة عبر OpenAI") },
            text = {
                Text(
                    "سيُرسل الوصف إلى خدمة OpenAI لتوليد صورة واحدة عمودية 1024×1536 بنموذج gpt-image-1-mini وجودة منخفضة للتجربة. هذه عملية مدفوعة محتملة من رصيد API، وليست مجانية بالضرورة. هل توافق على إرسال الوصف؟"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImage = false
                    val promptForJob = (if (result.isBlank()) idea.trim() else result).take(32000)
                    val secretReader = readOpenAiKey
                    generating = true
                    imageError = ""
                    imageBytes = null
                    Thread {
                        val resultJob = runCatching {
                            ImageGenerationClient.generate(secretReader(), promptForJob)
                        }
                        mainHandler.post {
                            resultJob.onSuccess {
                                imageBytes = it
                                imageError = "وصلت الصورة. احفظها من الزر أدناه."
                            }.onFailure {
                                imageError = it.message ?: "تعذّر توليد الصورة. راجع حساب API."
                            }
                            generating = false
                        }
                    }.start()
                }) { Text("أوافق وأولّد الصورة") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImage = false }) { Text("إلغاء") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(if (video) "استوديو الفيديو" else "استوديو الصور والقوالب",
            color = ToolGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            if (video) "البرومبت مجاني، وفي الأسفل أداة إنتاج مقطع MP4 من صورة بدون إنترنت. توليد حركة شخصيات بالذكاء غير مفعّل بعد."
            else "إنشاء البرومبت مجاني محلياً. ويمكن توليد صورة سحابية اختيارياً باستخدام OpenAI API بعد تأكيد التكلفة.",
            color = ToolText, fontSize = 13.sp
        )
        OutlinedTextField(
            value = idea,
            onValueChange = { if (it.length <= 800) idea = it },
            label = { Text("اكتب فكرتك بالعربي") },
            minLines = 3, maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                result = if (video) {
                    """
                    فيديو سينمائي عمودي 9:16 بجودة عالية ومدّة 8–10 ثوانٍ.
                    الفكرة الأساسية: ${idea.trim()}.
                    لقطة افتتاح جذّابة في أول ثانية، ومشهد مترابط بصرياً بإضاءة واقعية وحركة كاميرا ناعمة.
                    ثبّت ملامح الشخصيات واللباس والمكان في جميع اللقطات، بدون تشوهات أو تبديل للهوية.
                    اجعل النهاية تمهيداً طبيعياً لجزء ثانٍ بالشخصيات نفسها.
                    إذا وُجد حوار عربي، احرص على مزامنة دقيقة لحركة الشفاه مع الكلمات المنطوقة.
                    بدون شعارات أو نصوص عشوائية أو علامات مائية.
                    """.trimIndent()
                } else {
                    """
                    صورة احترافية واقعية عمودية بنسبة 9:16.
                    الفكرة: ${idea.trim()}.
                    تكوين بصري سينمائي متوازن، خامات طبيعية دقيقة، عمق مجال واقعي، إضاءة ناعمة موجهة،
                    تدرجات ألوان مدروسة وحفظ ملامح الشخصيات المرجعية إذا كانت موجودة.
                    تفاصيل واضحة بلا أصابع مشوّهة أو نصوص عشوائية أو علامات مائية.
                    """.trimIndent()
                }
                copied = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = idea.isNotBlank()
        ) { Text("إنشاء البرومبت") }
        if (result.isNotBlank()) {
            Surface(color = ToolSurface, shape = RoundedCornerShape(16.dp)) {
                Text(result, modifier = Modifier.padding(16.dp), color = Color.White,
                    textAlign = TextAlign.Start)
            }
            OutlinedButton(onClick = {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("SHBEEB AI prompt", result))
                copied = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (copied) "تم النسخ" else "نسخ البرومبت")
            }
        }
        if (video) {
            VideoMakerControls()
        }
        if (!video && kind == "الصور") {
            OfflineImageEditor()
            Text("توليد صورة حقيقية (اختياري)", color = ToolGold,
                fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (!hasOpenAiKey) {
                Text("أدخل مفتاح OpenAI API من إعدادات التطبيق لتفعيل التوليد السحابي.",
                    color = ToolText, fontSize = 13.sp)
            }
            Button(
                onClick = { pendingImage = true },
                enabled = !generating && hasOpenAiKey && idea.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (generating) "جارٍ توليد الصورة…" else "توليد صورة عبر OpenAI") }
            if (imageError.isNotBlank()) {
                Text(imageError, color = ToolText, fontSize = 13.sp)
            }
            val imagePreview = remember(imageBytes) {
                imageBytes?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            if (imagePreview != null) {
                Image(
                    bitmap = imagePreview,
                    contentDescription = "الصورة الجديدة الناتجة من الخدمة",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentScale = ContentScale.Fit
                )
                Button(
                    onClick = { saveImage.launch("shbeeb-image.png") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حفظ الصورة PNG على الهاتف") }
            }
        }
    }
}

/** Never calls, sends messages, changes settings or deletes data autonomously. */
@Composable
internal fun SafeDeviceScreen() {
    val context = LocalContext.current
    var number by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("تأكيد فتح تطبيق خارجي") },
            text = {
                Text(
                    when (pending) {
                        "settings" -> "بدك تفتح إعدادات الهاتف؟ لن نغيّر أي إعداد تلقائياً."
                        "dial" -> "بدك تفتح لوحة الاتصال بالرقم المكتوب؟ لن يتم إجراء المكالمة تلقائياً."
                        else -> "بدك تفتح نافذة مشاركة النص؟ لن يُرسل إلى أي جهة قبل ما تختارها بنفسك."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pending
                    pending = null
                    runCatching {
                        val intent = when (action) {
                            "settings" -> Intent(Settings.ACTION_SETTINGS)
                            "dial" -> Intent(Intent.ACTION_DIAL,
                                Uri.parse("tel:${number.filter { it.isDigit() || it == '+' }}"))
                            else -> Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sharing)
                            }
                        }
                        context.startActivity(
                            if (action == "share") Intent.createChooser(intent, "مشاركة النص")
                            else intent
                        )
                        status = "فُتحت واجهة النظام بقرارك. لا يوجد تنفيذ تلقائي."
                    }.onFailure { status = "تعذّر فتح التطبيق المطلوب على هذا الجهاز." }
                }) { Text("متابعة") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("إلغاء") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("التحكم الآمن بالهاتف", color = ToolGold, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "أدوات مباشرة بموافقتك: فتح إعدادات أندرويد أو لوحة الاتصال أو نافذة المشاركة. لا توجد صلاحية وصول أو إرسال آلي أو مراقبة للجهاز.",
            color = ToolText, fontSize = 13.sp
        )
        Button(onClick = { pending = "settings" }, modifier = Modifier.fillMaxWidth()) {
            Text("فتح إعدادات الهاتف")
        }
        OutlinedTextField(
            value = number,
            onValueChange = { if (it.length <= 32) number = it },
            label = { Text("رقم لعرضه في لوحة الاتصال") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { pending = "dial" },
            enabled = number.any { it.isDigit() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("فتح لوحة الاتصال فقط") }
        OutlinedTextField(
            value = sharing,
            onValueChange = { if (it.length <= 2000) sharing = it },
            label = { Text("نص تريد مشاركته") },
            modifier = Modifier.fillMaxWidth(), minLines = 2
        )
        OutlinedButton(
            onClick = { pending = "share" },
            enabled = sharing.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("فتح نافذة المشاركة") }
        if (status.isNotBlank()) Text(status, color = ToolText)
    }
}
