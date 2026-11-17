package com.shbeeb.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** One independent, encrypted and removable secret per cloud provider. */
@Composable
internal fun ProviderKeySettings(engine: ChatEngine, onChange: () -> Unit) {
    val context = LocalContext.current
    val credentials = remember(context) { SecureApiKeys(context) }
    var draft by remember(engine) { mutableStateOf("") } // Not rememberSaveable.
    var showDraft by remember(engine) { mutableStateOf(false) }
    var stored by remember(engine) { mutableStateOf(credentials.has(engine)) }
    var status by remember(engine) { mutableStateOf("") }
    var pendingDelete by remember(engine) { mutableStateOf(false) }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("حذف مفتاح ${engine.title}") },
            text = { Text("رح ينحذف المفتاح المحفوظ من هذا الهاتف فقط، وما رح يقدر التطبيق يتصل بالخدمة لحد ما تدخل مفتاح جديد.") },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { credentials.delete(engine) }
                    result.onSuccess {
                        stored = false
                        draft = ""
                        showDraft = false
                        status = "تم حذف مفتاح ${engine.title} المحفوظ على الجهاز."
                        onChange()
                    }.onFailure {
                        status = "تعذّر حذف المفتاح. جرّب مرة ثانية."
                    }
                    pendingDelete = false
                }) { Text("حذف المفتاح") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("إلغاء") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (stored) "مفتاح ${engine.title}: محفوظ ومشفّر على هذا الهاتف"
            else "مفتاح ${engine.title}: لم يُحفظ بعد",
            color = Color(0xFFF7CB78)
        )
        Text(
            "لأمانك، المفتاح المحفوظ لا يظهر مرة ثانية. يمكنك استبداله أو حذفه بأي وقت. تخزينه على الجهاز لا يضمن أن عندك رصيد API.",
            color = Color(0xFFB7C8D8)
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 300) draft = it },
            label = { Text(if (stored) "أدخل مفتاحاً جديداً للاستبدال" else "الصق مفتاح API هنا") },
            visualTransformation = if (showDraft)
                VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedButton(
                onClick = { showDraft = !showDraft },
                modifier = Modifier.weight(1f)
            ) { Text(if (showDraft) "إخفاء" else "إظهار مؤقتاً") }
            Button(
                onClick = {
                    val result = runCatching { credentials.save(engine, draft) }
                    result.onSuccess {
                        stored = true
                        draft = ""
                        showDraft = false
                        status = "حُفظ المفتاح مشفراً واختفى من خانة الإدخال."
                        onChange()
                    }.onFailure {
                        status = it.message ?: "تعذّر حفظ المفتاح."
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("حفظ المفتاح") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(ProviderKeyLinks.url(engine)))
                        )
                    }.onFailure { status = "لم أستطع فتح الرابط. افتح صفحة المفاتيح من المتصفح." }
                },
                modifier = Modifier.weight(1f)
            ) { Text("كيف آخذ المفتاح؟") }
            OutlinedButton(
                onClick = { pendingDelete = true },
                enabled = stored,
                modifier = Modifier.weight(1f)
            ) { Text("حذف المحفوظ") }
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFFB7C8D8))
    }
}
