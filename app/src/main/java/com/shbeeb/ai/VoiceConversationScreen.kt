package com.shbeeb.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Explicit, in-app conversational voice mode: listen -> text request -> speak reply -> listen.
 * SpeechRecognizer is an Android/system service and MAY use the network even with
 * EXTRA_PREFER_OFFLINE=true. TTS voices can also require a language download.
 * No recording in the background; user has a Stop button.
 */
@Composable
internal fun VoiceConversationScreen(
    engine: ChatEngine,
    messages: List<ChatEntry>,
    busy: Boolean,
    configured: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    // BCP-47 language tags: "ar" alone may be rejected by a device's recognizer (error 12).
    // Keep the user's choice during configuration changes but never store recorded speech.
    var recognitionLanguage by rememberSaveable { mutableStateOf("ar-SA") }
    var languageExpanded by remember { mutableStateOf(false) }
    val availableLanguages = listOf(
        "ar-SA" to "العربية — السعودية",
        "ar-AE" to "العربية — الإمارات",
        "ar-EG" to "العربية — مصر",
        "auto" to "لغة الهاتف تلقائياً"
    )
    var active by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var awaitingReply by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED)
    }
    var confirmCloud by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اضغط بدء لتتحدث، ثم تسمع الرد تلقائياً.") }
    var lastQuestion by remember { mutableStateOf("") }
    var lastAnswer by remember { mutableStateOf("") }
    var assistantCountBefore by remember { mutableStateOf(0) }
    val currentOnSend = rememberUpdatedState(onSend)
    val currentActive = rememberUpdatedState(active)

    val recognizer = remember(context) {
        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context))
                SpeechRecognizer.createSpeechRecognizer(context)
            else null
        }.getOrNull()
    }
    val speech = remember(context) {
        TextToSpeech(context) { code ->
            handler.post { ttsReady = code == TextToSpeech.SUCCESS }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            active = true
            status = "جارٍ تجهيز الميكروفون..."
        } else {
            status = "ما تم منح إذن الميكروفون. يمكنك تفعيله من إعدادات أندرويد."
        }
    }

    fun start() {
        if (!configured) {
            status = "اختَر المحرك واضبطه أو استورد نموذج ذكاء الهاتف أولاً."
            return
        }
        if (recognizer == null) {
            status = "خدمة التعرف على الكلام غير متوفرة على هذا الهاتف."
            return
        }
        if (!ttsReady) {
            status = "خدمة النطق غير جاهزة. تأكد من تثبيت محرّك نطق يدعم العربية."
            return
        }
        if (speech.isLanguageAvailable(Locale.forLanguageTag("ar")) <
            TextToSpeech.LANG_AVAILABLE) {
            status = "محرّك النطق لا يملك صوتاً عربياً. ثبّت اللغة العربية في إعدادات تحويل النص إلى كلام."
            return
        }
        if (!permissionGranted) {
            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        active = true
        status = "جارٍ فتح الميكروفون. تفضل احكي."
    }

    DisposableEffect(recognizer, speech) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (currentActive.value) status = "عم اسمعك... احكي سؤالك."
            }
            override fun onBeginningOfSpeech() { status = "عم استمع لسؤالك..." }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { status = "عم حوّل كلامك إلى نص..." }
            override fun onError(code: Int) {
                listening = false
                if (currentActive.value) {
                    active = false
                    status = when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "ما سمعت سؤال واضح. اضغط بدء وجرب مجدداً."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "خدمة التعرف على الكلام تحتاج شبكة أو حزمة لغة غير متصلة. جرّب الإملاء بعد تنزيل اللغة."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "اللغة المحددة غير مدعومة بخدمة التعرّف على الكلام (12). اختر لهجة أخرى أو لغة الهاتف، وتأكد من تحديث خدمات التعرّف على الكلام."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "اللغة المحددة مدعومة لكن حزمة التعرف غير متاحة حالياً (13). اتصل بالإنترنت أو نزّل حزمة اللغة من إعدادات الهاتف."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "إذن الميكروفون غير متاح. راجعه من إعدادات الهاتف."
                        else -> "توقف التعرف على الصوت (رمز $code). اضغط بدء للمحاولة."
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull()?.trim().orEmpty()
                if (currentActive.value && text.isNotBlank() && text.length <= 3000) {
                    lastQuestion = text
                    assistantCountBefore = messages.count { it.role == "assistant" }
                    awaitingReply = true
                    status = "سمعت: $text — جارٍ انتظار رد ${engine.title}..."
                    currentOnSend.value(text)
                } else if (currentActive.value) {
                    active = false
                    status = "لم أفهم السؤال. اضغط بدء وتحدث بوضوح."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer?.setRecognitionListener(listener)
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.postDelayed({
                    speaking = false
                    if (currentActive.value) status = "خلص الرد، عم اسمع سؤالك التالي..."
                }, 650)
            }
            override fun onError(utteranceId: String?) {
                handler.post {
                    speaking = false
                    active = false
                    status = "تعذرت قراءة الرد بصوت عربي. جرّب إعدادات النطق في الهاتف."
                }
            }
        })
        onDispose {
            active = false
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            runCatching { speech.stop() }
            runCatching { speech.shutdown() }
        }
    }

    // React only to a new assistant entry, not to the previous answer already displayed.
    LaunchedEffect(messages.size, busy, error, awaitingReply, active) {
        if (active && awaitingReply && !busy) {
            val answers = messages.filter { it.role == "assistant" }
            if (answers.size > assistantCountBefore) {
                awaitingReply = false
                lastAnswer = answers.last().text
                if (ttsReady && lastAnswer.isNotBlank()) {
                    speaking = true
                    listening = false
                    speech.language = Locale.forLanguageTag("ar")
                    status = "عم جاوبك بالصوت..."
                    val code = speech.speak(
                        lastAnswer.take(3000), TextToSpeech.QUEUE_FLUSH,
                        null, "shbeeb-voice-reply"
                    )
                    if (code != TextToSpeech.SUCCESS) {
                        speaking = false
                        active = false
                        status = "تعذر تشغيل صوت الرد."
                    }
                } else {
                    active = false
                    status = "ما في صوت عربي جاهز للرد."
                }
            } else if (!error.isNullOrBlank()) {
                active = false
                awaitingReply = false
                status = "فشل الرد: $error"
            }
        }
    }

    LaunchedEffect(active, listening, awaitingReply, speaking, busy, permissionGranted) {
        if (active && !listening && !awaitingReply && !speaking && !busy &&
            permissionGranted && recognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (recognitionLanguage != "auto") {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguage)
                }
                // Do not force offline recognition: the device may not have Arabic voice packs.
                // The selected recognizer may use the network even for local LLM conversations.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            listening = true
            runCatching { recognizer.startListening(intent) }.onFailure {
                listening = false
                active = false
                status = "تعذّر تشغيل الميكروفون. تأكد من إعدادات التعرف على الكلام."
            }
        }
    }

    if (confirmCloud) {
        AlertDialog(
            onDismissRequest = { confirmCloud = false },
            title = { Text("موافقة على المحادثة الصوتية السحابية") },
            text = {
                Text("كل سؤال صوتي يتم تحويله إلى نص وإرساله إلى ${engine.title} وقد يستهلك رصيد API. التعرّف على الصوت يعتمد على خدمة الهاتف وقد يستخدم الإنترنت. هل تريد بدء المحادثة؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloud = false
                    start()
                }) { Text("أوافق وأبدأ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloud = false }) { Text("إلغاء") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("المحادثة الصوتية", fontSize = 23.sp, fontWeight = FontWeight.Bold,
                color = OnePrimary)
            TextButton(onClick = {
                active = false
                listening = false
                awaitingReply = false
                speech.stop()
                recognizer?.cancel()
                onClose()
            }) { Text("رجوع") }
        }
        Text("المحرّك: ${engine.title}", color = OnePrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("لغة التعرف على الكلام", color = OneInk)
            Box {
                OutlinedButton(onClick = { languageExpanded = true }, enabled = !active) {
                    Text(availableLanguages.firstOrNull { it.first == recognitionLanguage }?.second
                        ?: "العربية — السعودية")
                }
                DropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    availableLanguages.forEach { (tag, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                recognitionLanguage = tag
                                languageExpanded = false
                                status = "تم اختيار $title. اضغط بدء المحادثة للتجربة."
                            }
                        )
                    }
                }
            }
        }
        Surface(color = OneMint, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        speaking -> "جارٍ الرد بصوت…"
                        listening -> "أستمع إليك…"
                        busy || awaitingReply -> "جارٍ تحضير الرد…"
                        active -> "جاهز للاستماع…"
                        else -> "الميكروفون متوقف"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnePrimaryDark
                )
                Text(status, color = OneMuted)
            }
        }
        if (!active) {
            Button(
                onClick = {
                    if (engine == ChatEngine.LOCAL || engine == ChatEngine.OLLAMA) start()
                    else confirmCloud = true
                },
                enabled = configured && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("بدء المحادثة الصوتية") }
        } else {
            OutlinedButton(onClick = {
                active = false
                listening = false
                awaitingReply = false
                recognizer?.cancel()
                speech.stop()
                status = "توقفت المحادثة الصوتية."
            }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف المحادثة") }
        }
        if (lastQuestion.isNotBlank()) {
            Text("أنت: $lastQuestion", color = OneInk)
        }
        if (lastAnswer.isNotBlank()) {
            Text("${engine.title}: $lastAnswer", color = OneInk)
        }
        Text(
            "تعمل المحادثة فقط أثناء بقاء هذه الشاشة مفتوحة وبعد منح إذن الميكروفون. التعرف على الكلام قد يحتاج الإنترنت حتى لو كان ذكاء الهاتف محلياً؛ جرّب تغيير لغة التعرف إذا ظهر الرمز 12. أوقف المحادثة في أي وقت.",
            fontSize = 12.sp, color = OneMuted
        )
    }
}
