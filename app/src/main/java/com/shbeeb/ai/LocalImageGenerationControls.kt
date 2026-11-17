package com.shbeeb.ai

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun LocalImageGenerationControls(promptSource: String) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose { LocalDiffusionEngine.release() }
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var modelUri by remember(context) { mutableStateOf(LocalImageModelManager.savedUri(context)) }
    var modelLabel by remember { mutableStateOf(if (modelUri == null) "لم يتم اختيار نموذج صور" else "نموذج صور محفوظ على الجهاز") }
    var prompt by remember(promptSource) { mutableStateOf(promptSource) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var askingDownload by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var cancelDownload: AtomicBoolean? by remember { mutableStateOf(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    val selectedModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { LocalImageModelManager.verifySelectedFile(context, uri) }
                .onSuccess {
                    modelUri = uri
                    modelLabel = it
                    status = "تم اختيار النموذج. لا تُرسل الأوصاف إلى الإنترنت عند التوليد المحلي."
                }
                .onFailure { status = it.message ?: "تعذر اعتماد ملف النموذج." }
        }
    }
    val saveModel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val canceled = AtomicBoolean(false)
            cancelDownload = canceled
            downloading = true
            progress = 0
            status = "بدء تنزيل نموذج SD 1.5 إلى الموقع الذي اخترته…"
            val appContext = context.applicationContext
            Thread {
                val result = runCatching {
                    LocalImageModelManager.download(appContext, uri, canceled) { done, total ->
                        val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                        handler.post { progress = percent; status = "تنزيل النموذج: $percent%" }
                    }
                }
                handler.post {
                    result.onSuccess {
                        modelUri = uri
                        modelLabel = LocalImageModelManager.displayName(context, uri)
                        status = "اكتمل التنزيل والتحقق من SHA-256. النموذج جاهز محلياً."
                    }.onFailure {
                        status = it.message ?: "تعذر تنزيل النموذج."
                    }
                    downloading = false
                    cancelDownload = null
                }
            }.start()
        }
    }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = image
        if (uri != null && bitmap != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر كتابة PNG." }
                } ?: error("تعذر فتح مكان الحفظ.")
                status = "حُفظت الصورة في الموقع الذي اخترته."
            }.onFailure { status = it.message ?: "تعذر حفظ الصورة." }
        }
    }

    if (askingDownload) {
        AlertDialog(
            onDismissRequest = { askingDownload = false },
            title = { Text("تنزيل نموذج صور محلي") },
            text = {
                Text("حجم النموذج 1.57 GB، ويستهلك بيانات الإنترنت ومساحة تخزين بهذا المقدار تقريباً. سيُنزّل مباشرة إلى ملف تختاره أنت، ويتحقق التطبيق من بصمة SHA-256 قبل اعتماده. النموذج Stable Diffusion 1.5 بترخيص CreativeML OpenRAIL-M؛ هذا نموذج صور وليس نموذج محادثة. هل تريد المتابعة؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    askingDownload = false
                    saveModel.launch(LocalImageModelManager.MODEL_NAME)
                }) { Text("اختيار مكان التنزيل") }
            },
            dismissButton = { TextButton(onClick = { askingDownload = false }) { Text("إلغاء") } }
        )
    }

    Surface(color = OneSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("توليد صور محلياً", color = OnePrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "يعمل على الهاتف دون إرسال وصفك إلى خدمة صور. النموذج 1.57 GB، والدقة 512×512. السرعة والحرارة تعتمد على الجهاز؛ لم يُختبر الأداء بعد على OPPO Find X5. أفضل النتائج عادةً مع وصف إنجليزي لأن SD 1.5 أقدم وأضعف في فهم العربية.",
                color = OneMuted, fontSize = 12.sp
            )
            Text("النموذج: $modelLabel", color = OneInk, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { selectedModel.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !downloading && !generating,
                    modifier = Modifier.weight(1f)
                ) { Text("اختيار ملف SD 1.5") }
                Button(
                    onClick = { askingDownload = true },
                    enabled = !downloading && !generating,
                    modifier = Modifier.weight(1f)
                ) { Text("تنزيل 1.57 GB") }
            }
            if (downloading) {
                Text("$progress% — يمكنك الإلغاء؛ سيحذف التطبيق الملف غير المكتمل إذا سمح موفر الملفات.", color = OneMuted, fontSize = 12.sp)
                OutlinedButton(onClick = { cancelDownload?.set(true) }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء التنزيل") }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { if (it.length <= 1200) prompt = it },
                label = { Text("الوصف — العربية أو الإنجليزية (الإنجليزية أدق)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                enabled = !generating && !downloading
            )
            Button(
                onClick = {
                    val uri = modelUri ?: return@Button
                    generating = true
                    status = "تحميل النموذج إلى الذاكرة…"
                    progress = 0
                    image = null
                    val promptValue = prompt.trim()
                    Thread {
                        val generated = runCatching {
                            LocalDiffusionEngine.loadModel(context, uri)
                            LocalDiffusionEngine.generate(promptValue) { completed, total ->
                                val percent = if (total > 0) ((completed * 100) / total).toInt().coerceIn(0, 100) else 0
                                handler.post { progress = percent; status = "توليد محلي: $percent%" }
                            }
                        }
                        handler.post {
                            generated.onSuccess { bytes ->
                                image = runCatching {
                                    val pixels = IntArray(512 * 512) { index ->
                                        val offset = index * 3
                                        val red = bytes[offset].toInt() and 0xff
                                        val green = bytes[offset + 1].toInt() and 0xff
                                        val blue = bytes[offset + 2].toInt() and 0xff
                                        android.graphics.Color.rgb(red, green, blue)
                                    }
                                    Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
                                }.getOrNull()
                                status = if (image != null) "اكتمل التوليد محلياً. احفظ الصورة للاحتفاظ بها." else "اكتمل المحرك لكن تعذر عرض الصورة الناتجة."
                            }.onFailure {
                                status = it.message ?: "تعذر توليد الصورة محلياً."
                            }
                            generating = false
                        }
                    }.start()
                },
                enabled = modelUri != null && prompt.isNotBlank() && !generating && !downloading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (generating) "جارٍ التوليد…" else "توليد صورة على الهاتف") }
            if (generating) {
                Text("$progress% — المعالجة محلية وقد تستغرق وقتاً. أوقفها إذا ارتفعت حرارة الهاتف.", color = OneMuted, fontSize = 12.sp)
                OutlinedButton(onClick = { LocalDiffusionEngine.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء التوليد") }
            }
            image?.let { bitmap ->
                Spacer(Modifier.height(3.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "صورة مولدة محلياً على الجهاز",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Button(onClick = { saveImage.launch("shbeeb-one-local-image.png") }, modifier = Modifier.fillMaxWidth()) {
                    Text("حفظ الصورة PNG")
                }
            }
            if (status.isNotBlank()) Text(status, color = OneMuted, fontSize = 12.sp)
        }
    }
}
