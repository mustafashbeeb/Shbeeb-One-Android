package com.shbeeb.ai

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import java.io.OutputStreamWriter
import java.util.Locale
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val Navy = Color(0xFF050B13)
private val Panel = Color(0xFF0D1B2A)
private val Blue = Color(0xFF35D6FF)
private val Gold = Color(0xFFFFC857)
private val Muted = Color(0xFFB7C8D8)
private val Purple = Color(0xFF648BFF)
private val GlassEdge = Color(0xFF246184)
private val GlassShine = Color(0xFF8BE9FF)

// Glass-inspired depth: translucent layered gradients, specular edging and soft elevation.
private fun Modifier.shbeebGlass(radius: Int = 20, elevation: Int = 12): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .shadow(elevation.dp, shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xD9122639),
                    Color(0xE00C1C2B),
                    Color(0xF007111C)
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color(0xC98BE9FF),
                    Color(0x8E35D6FF),
                    Color(0x55FFC857),
                    Color(0x665B8DCD)
                )
            ),
            shape
        )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShbeebApp() }
    }
}

@Composable
private fun ShbeebApp() {
    var screen by rememberSaveable { mutableStateOf("home") }
    var chosenFeature by rememberSaveable { mutableStateOf("") }
    // Saved provider credentials are encrypted by Android Keystore, not Compose state.
    var chatBusy by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }
    val uiHandler = remember { Handler(Looper.getMainLooper()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val secureApiKeys = remember(context) { SecureApiKeys(context) }
    var apiKeyRevision by remember { mutableStateOf(0) }
    var connectionStatus by remember { mutableStateOf("") }
    var connectionBusy by remember { mutableStateOf(false) }
    val preferences = androidx.compose.runtime.remember(context) {
        context.getSharedPreferences("shbeeb_settings", android.content.Context.MODE_PRIVATE)
    }
    var localModelInstalled by remember(context) {
        mutableStateOf(LocalModelFiles.isInstalled(context))
    }
    var engineId by rememberSaveable {
        mutableStateOf(preferences.getString("selected_engine", "openai") ?: "openai")
    }
    val chatStore = remember(context) { ChatHistoryStore(context) }
    var historyEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("history_enabled", false))
    }
    val chatMessages = remember(context) {
        mutableStateListOf<ChatEntry>().apply {
            if (historyEnabled) addAll(chatStore.load(engineId))
        }
    }
    var openAiModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_openai", ChatEngine.OPENAI.defaultModel)
            ?: ChatEngine.OPENAI.defaultModel)
    }
    var claudeModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_claude", ChatEngine.CLAUDE.defaultModel)
            ?: ChatEngine.CLAUDE.defaultModel)
    }
    var geminiModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_gemini", ChatEngine.GEMINI.defaultModel)
            ?: ChatEngine.GEMINI.defaultModel)
    }
    var deepSeekModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_deepseek", ChatEngine.DEEPSEEK.defaultModel)
            ?: ChatEngine.DEEPSEEK.defaultModel)
    }
    var openRouterModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_openrouter", ChatEngine.OPENROUTER.defaultModel)
            ?: ChatEngine.OPENROUTER.defaultModel)
    }
    var ollamaBase by rememberSaveable {
        mutableStateOf(preferences.getString("ollama_host", "") ?: "")
    }
    var ollamaModel by rememberSaveable {
        mutableStateOf(preferences.getString("ollama_model", "llama3.2:1b") ?: "llama3.2:1b")
    }
    var localResponseMode by rememberSaveable {
        mutableStateOf(
            preferences.getString("local_response_mode", LocalResponseMode.BALANCED.id)
                ?: LocalResponseMode.BALANCED.id
        )
    }
    val selectedEngine = ChatEngine.fromId(engineId)
    // Reading a stored secret is deferred until an explicit user-initiated request.
    val hasSelectedCloudKey = remember(selectedEngine, apiKeyRevision) {
        when (selectedEngine) {
            ChatEngine.OPENAI, ChatEngine.GEMINI, ChatEngine.CLAUDE,
            ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER ->
                secureApiKeys.has(selectedEngine)
            else -> false
        }
    }
    val selectedCloudModel = when (selectedEngine) {
        ChatEngine.OPENAI -> openAiModel
        ChatEngine.CLAUDE -> claudeModel
        ChatEngine.GEMINI -> geminiModel
        ChatEngine.DEEPSEEK -> deepSeekModel
        ChatEngine.OPENROUTER -> openRouterModel
        ChatEngine.OLLAMA, ChatEngine.LOCAL -> ""
    }
    val readyForAttempt = when (selectedEngine) {
        ChatEngine.LOCAL -> localModelInstalled
        ChatEngine.OLLAMA -> ollamaBase.isNotBlank() && ollamaModel.isNotBlank()
        else -> hasSelectedCloudKey
    }
    val selectEngine: (ChatEngine) -> Unit = { engine ->
        if (!chatBusy && engine != selectedEngine) {
            engineId = engine.id
            preferences.edit().putString("selected_engine", engine.id).apply()
            chatMessages.clear() // Never forward one provider's conversation to another.
            if (historyEnabled) chatMessages.addAll(chatStore.load(engine.id))
            chatError = null
            connectionStatus = ""
        }
    }
    var assistantName by rememberSaveable {
        mutableStateOf(preferences.getString("assistant_name", "المساعد الذكي") ?: "المساعد الذكي")
    }
    // First-run introduction is kept local to this device.
    var showWelcome by rememberSaveable {
        mutableStateOf(!preferences.getBoolean("welcome_seen", false))
    }
    // Preserve the chat composer when the keyboard takes up most of the screen.
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            secondary = Gold,
            background = Navy,
            surface = Panel,
            onSurface = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Navy, Color(0xFF071827), Color(0xFF081B2C), Navy)))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Purple.copy(alpha = .14f), Color.Transparent)
                        ),
                        radius = size.minDimension * .85f,
                        center = Offset(size.width * .93f, size.height * .18f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Blue.copy(alpha = .17f), Color.Transparent)
                        ),
                        radius = size.minDimension * .75f,
                        center = Offset(size.width * .04f, size.height * .73f)
                    )
                }
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    if (showWelcome) {
                        WelcomeScreen {
                            showWelcome = false
                            preferences.edit().putBoolean("welcome_seen", true).apply()
                        }
                    } else {
                        Header { screen = "home" }
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            "assistant" -> AssistantScreen(
                                assistantName = assistantName,
                                engine = selectedEngine,
                                messages = chatMessages,
                                apiConfigured = readyForAttempt,
                                busy = chatBusy,
                                error = chatError,
                                onEngineSelected = selectEngine,
                                openSettings = { screen = "settings" },
                                onSend = { question ->
                                    if (!chatBusy && readyForAttempt && question.isNotBlank()) {
                                        val userEntry = ChatEntry("user", question)
                                        chatMessages.add(userEntry)
                                        if (historyEnabled) runCatching { chatStore.append(engineId, userEntry) }
                                            .onFailure { chatError = "تعذّر حفظ الرسالة على الجهاز." }
                                        val history = chatMessages.takeLast(12).toList()
                                        val engineForRequest = selectedEngine
                                        // Decrypt on a worker only for the user's own request.
                                        val ollamaHostForRequest = ollamaBase
                                        val ollamaModelForRequest = ollamaModel
                                        val cloudModelForRequest = selectedCloudModel
                                        val localModeForRequest = LocalResponseMode.fromId(localResponseMode)
                                        chatError = null
                                        chatBusy = true
                                        Thread {
                                            val result = runCatching {
                                                if (engineForRequest == ChatEngine.LOCAL) {
                                                    LocalOnDeviceEngine.reply(context, question, localModeForRequest)
                                                } else {
                                                    val keyForRequest = if (engineForRequest == ChatEngine.OLLAMA) ""
                                                        else secureApiKeys.read(engineForRequest)
                                                    ChatTransport.reply(
                                                        engineForRequest, keyForRequest, history,
                                                        ollamaHostForRequest, ollamaModelForRequest,
                                                        cloudModelForRequest
                                                    )
                                                }
                                            }
                                            uiHandler.post {
                                                result.onSuccess { answer ->
                                                    val assistantEntry = ChatEntry("assistant", answer)
                                                    chatMessages.add(assistantEntry)
                                                    if (historyEnabled) runCatching {
                                                        chatStore.append(engineForRequest.id, assistantEntry)
                                                    }.onFailure { chatError = "وصل الرد لكن تعذّر حفظه محلياً." }
                                                }.onFailure { failure ->
                                                    chatError = failure.message ?: "تعذّر الاتصال بالمحرّك."
                                                }
                                                chatBusy = false
                                            }
                                        }.start()
                                    }
                                }
                            )
                            "engines" -> EnginesScreen(localModelInstalled)
                            "diagnostics" -> DeviceDiagnosticsScreen()
                            "guide" -> UserGuideScreen()
                            "settings" -> SettingsScreen(
                                onOpenGuide = { screen = "guide" },
                                onKeysChanged = {
                                    apiKeyRevision += 1
                                    connectionStatus = ""
                                },
                                connectionBusy = connectionBusy,
                                connectionStatus = connectionStatus,
                                onCheckConnection = {
                                    if (!connectionBusy) {
                                        val engineToCheck = selectedEngine
                                        val modelToCheck = selectedCloudModel
                                        val ollamaHostToCheck = ollamaBase
                                        val ollamaModelToCheck = ollamaModel
                                        connectionBusy = true
                                        connectionStatus = "جارٍ اختبار الاتصال بدون توليد رسالة مدفوعة…"
                                        Thread {
                                            val report = runCatching {
                                                val key = when (engineToCheck) {
                                                    ChatEngine.OPENAI, ChatEngine.CLAUDE, ChatEngine.GEMINI,
                                                    ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER ->
                                                        secureApiKeys.read(engineToCheck)
                                                    else -> ""
                                                }
                                                ProviderConnectionProbe.check(
                                                    context, engineToCheck, key, modelToCheck,
                                                    ollamaHostToCheck, ollamaModelToCheck
                                                )
                                            }
                                            uiHandler.post {
                                                connectionStatus = report.getOrNull()?.message
                                                    ?: "فشل فحص الاتصال. راجع إعدادات المحرك والإنترنت."
                                                connectionBusy = false
                                            }
                                        }.start()
                                    }
                                },
                                onCheckRealConnection = {
                                    if (!connectionBusy && selectedEngine != ChatEngine.LOCAL) {
                                        val engineForTest = selectedEngine
                                        val modelForTest = selectedCloudModel
                                        val hostForTest = ollamaBase
                                        val ollamaModelForTest = ollamaModel
                                        connectionBusy = true
                                        connectionStatus = "جارٍ اختبار رسالة قصيرة، بعد موافقتك على التكلفة المحتملة…"
                                        Thread {
                                            val result = runCatching {
                                                val key = if (engineForTest == ChatEngine.OLLAMA) ""
                                                    else secureApiKeys.read(engineForTest)
                                                ChatTransport.reply(
                                                    engineForTest, key, listOf(ChatEntry("user", "أجب بكلمة: جاهز")),
                                                    hostForTest, ollamaModelForTest, modelForTest,
                                                    diagnostic = true
                                                )
                                            }
                                            uiHandler.post {
                                                connectionStatus = result.fold(
                                                    onSuccess = { "نجح التوليد الحقيقي عبر ${engineForTest.title}: ${it.take(120)}" },
                                                    onFailure = { it.message ?: "فشل اختبار التوليد." }
                                                )
                                                connectionBusy = false
                                            }
                                        }.start()
                                    }
                                },
                                onOpenDiagnostics = { screen = "diagnostics" },
                                assistantName = assistantName,
                                engine = selectedEngine,
                                cloudModel = selectedCloudModel,
                                onCloudModelChanged = { newModel ->
                                    when (selectedEngine) {
                                        ChatEngine.OPENAI -> {
                                            openAiModel = newModel
                                            preferences.edit().putString("model_openai", newModel).apply()
                                        }
                                        ChatEngine.CLAUDE -> {
                                            claudeModel = newModel
                                            preferences.edit().putString("model_claude", newModel).apply()
                                        }
                                        ChatEngine.GEMINI -> {
                                            geminiModel = newModel
                                            preferences.edit().putString("model_gemini", newModel).apply()
                                        }
                                        ChatEngine.DEEPSEEK -> {
                                            deepSeekModel = newModel
                                            preferences.edit().putString("model_deepseek", newModel).apply()
                                        }
                                        ChatEngine.OPENROUTER -> {
                                            openRouterModel = newModel
                                            preferences.edit().putString("model_openrouter", newModel).apply()
                                        }
                                        ChatEngine.OLLAMA, ChatEngine.LOCAL -> Unit
                                    }
                                },
                                ollamaBase = ollamaBase,
                                ollamaModel = ollamaModel,
                                onLocalStatusChanged = {
                                    localModelInstalled = LocalModelFiles.isInstalled(context)
                                },
                                localResponseMode = LocalResponseMode.fromId(localResponseMode),
                                onLocalResponseModeChanged = { mode ->
                                    localResponseMode = mode.id
                                    preferences.edit().putString("local_response_mode", mode.id).apply()
                                },
                                historyEnabled = historyEnabled,
                                onHistoryEnabledChanged = { enabled ->
                                    if (enabled && !historyEnabled) {
                                        // Explicit opt-in: preserve current session, then persist it.
                                        chatMessages.forEach { item ->
                                            runCatching { chatStore.append(selectedEngine.id, item) }
                                        }
                                    }
                                    historyEnabled = enabled
                                    preferences.edit().putBoolean("history_enabled", enabled).apply()
                                },
                                onDeleteHistory = {
                                    runCatching { chatStore.deleteAll() }
                                    chatMessages.clear()
                                },
                                 onOllamaBaseChanged = {
                                    ollamaBase = it
                                    preferences.edit().putString("ollama_host", it).apply()
                                },
                                onOllamaModelChanged = {
                                    ollamaModel = it
                                    preferences.edit().putString("ollama_model", it).apply()
                                },
                                onNameChanged = { name ->
                                    assistantName = name
                                    preferences.edit().putString("assistant_name", name).apply()
                                }
                            )
                            "feature" -> when (chosenFeature) {
                                "المهام" -> TasksScreen()
                                "ملفاتي" -> FilesScreen()
                                "الصور", "الفيديو", "القوالب" -> CreativeScreen(
                                    chosenFeature,
                                    hasOpenAiKey = secureApiKeys.has(ChatEngine.OPENAI),
                                    readOpenAiKey = { secureApiKeys.read(ChatEngine.OPENAI) }
                                )
                                "التحكم بالهاتف" -> SafeDeviceScreen()
                                else -> FeatureScreen(chosenFeature)
                            }
                            else -> HomeScreen(
                                assistantName = assistantName,
                                apiConfigured = readyForAttempt,
                                selectedEngine = selectedEngine,
                                localInstalled = localModelInstalled,
                                openAssistant = { screen = "assistant" },
                                openEngines = { screen = "engines" },
                                openAnythingLLM = {
                                    context.startActivity(Intent(context, com.anythingllm.AnythingActivity::class.java))
                                },
                                openFeature = {
                                    chosenFeature = it
                                    screen = "feature"
                                }
                            )
                        }
                    }
                        if (!keyboardVisible) BottomBar(screen) { screen = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 25.dp, vertical = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF12638A), Color(0xFF07131E))), CircleShape)
                .border(2.dp, Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            LogoMark()
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "مصنع الذكاء شبيب",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 29.sp,
            textAlign = TextAlign.Center
        )
        Text(
            "SHBEEB AI",
            color = Gold,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(27.dp))
        Surface(
            color = Color(0xD20D1B2A),
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier.fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(23.dp))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(GlassShine, Blue.copy(alpha = .6f), Gold.copy(alpha = .5f))),
                    RoundedCornerShape(23.dp)
                )
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text(
                    "نسخة أولية قيد التجربة",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text(
                    "مساعد عربي تجريبي. المحادثة السحابية اختيارية وتحتاج مفتاح API؛ المحرك المحلي يحتاج نموذجاً متوافقاً، والصوت يعتمد على خدمات الهاتف.",
                    color = Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("الدخول إلى المصنع", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "يمكن تغيير اسم المساعد لاحقاً من الإعدادات",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Header(goHome: () -> Unit) {
    Box(Modifier.fillMaxWidth()
        .shbeebGlass(radius = 0, elevation = 5)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(Modifier.clickable(onClick = goHome))
            Column(Modifier.weight(1f)) {
                Text("مصنع الذكاء شبيب", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("SHBEEB AI  •  إصدار 0.10.7 تجريبي", color = Blue, fontSize = 11.sp)
            }
            Text("✦", color = Blue, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Box(modifier.size(62.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val middle = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * .49f
            val inner = outer * .86f
            val gear = Path()
            for (i in 0 until 24) {
                val angle = (i * Math.PI / 12 - Math.PI / 2).toFloat()
                val radius = if (i % 2 == 0) outer else inner
                val point = Offset(
                    middle.x + cos(angle) * radius,
                    middle.y + sin(angle) * radius
                )
                if (i == 0) gear.moveTo(point.x, point.y)
                else gear.lineTo(point.x, point.y)
            }
            gear.close()
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Blue.copy(alpha = .35f), Color.Transparent)
                ),
                radius = outer * 1.2f
            )
            drawPath(
                path = gear,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFA6F0FF), Blue, Color(0xFF2875E8), Blue)
                )
            )
            drawPath(
                path = gear,
                color = Color(0xFFB3F2FF),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF10304A), Color(0xFF06111D))
                ),
                radius = outer * .72f
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(GlassShine, Blue, Color(0xFF2875E8), GlassShine)
                ),
                radius = outer * .71f,
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Blue.copy(alpha = .42f), Color(0xFF2875E8).copy(alpha = .08f))
                ),
                radius = outer * .40f
            )
        }
        Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        // Exact clockwise letter order: S - H - B - E - E - B.
        val letters = listOf(
            Triple("S", 0, -24),
            Triple("H", 21, -12),
            Triple("B", 21, 12),
            Triple("E", 0, 24),
            Triple("E", -21, 12),
            Triple("B", -21, -12)
        )
        letters.forEach { (letter, x, y) ->
            Text(
                letter,
                modifier = Modifier.align(Alignment.Center).offset(x = x.dp, y = y.dp),
                color = Color(0xFF17152B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private data class Feature(val title: String, val subtitle: String, val symbol: String)

private val features = listOf(
    Feature("المحادثة", "نص وإملاء صوتي", "◉"),
    Feature("محركات الذكاء", "محلي أو سحابي", "⌘"),
    Feature("الصور", "فلاتر وتوليد اختياري", "▧"),
    Feature("الفيديو", "MP4 صامت من صورة", "▷"),
    Feature("ملفاتي", "معاينة ملفات", "▣"),
    Feature("القوالب", "نصوص جاهزة", "◇"),
    Feature("المهام", "قائمة محلية", "☷"),
    Feature("التحكم بالهاتف", "يتطلب تأكيدك", "▤"),
    Feature("AnythingLLM", "محادثة ووثائق ونماذج", "✦")
)

@Composable
private fun HomeScreen(
    assistantName: String,
    apiConfigured: Boolean,
    selectedEngine: ChatEngine,
    localInstalled: Boolean,
    openAssistant: () -> Unit,
    openEngines: () -> Unit,
    openAnythingLLM: () -> Unit,
    openFeature: (String) -> Unit
) {
    val readiness = when {
        apiConfigured -> "جاهز للمحاولة"
        selectedEngine == ChatEngine.LOCAL -> "يحتاج نموذجاً"
        else -> "يحتاج إعداداً"
    }
    val engineDetail = when {
        apiConfigured -> "الإعداد مكتمل؛ لم يُختبر الاتصال بعد"
        selectedEngine == ChatEngine.LOCAL && !localInstalled -> "استورد نموذجاً متوافقاً للبدء على الهاتف"
        selectedEngine == ChatEngine.LOCAL -> "جرّب المحرك للتأكد من عمل النموذج"
        else -> "أكمل إعداد المحرك من صفحة المحركات"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("أهلاً بك", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                Text("مساعدك الذكي في مكان واحد", color = Muted, fontSize = 13.sp)
            }
            Surface(
                color = Blue.copy(alpha = .12f),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.border(1.dp, Blue.copy(alpha = .55f), RoundedCornerShape(30.dp))
            ) {
                Text("نسخة تجريبية", color = Blue, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .shbeebGlass(radius = 23, elevation = 10)
                .background(Brush.horizontalGradient(listOf(Color(0xFF102A40), Color(0xFF0A1827))))
                .clickable(onClick = openAssistant)
        ) {
            Spacer(
                Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter)
                    .background(Brush.horizontalGradient(
                        listOf(Color.Transparent, Blue.copy(alpha = .9f), GlassShine, Color.Transparent)
                    ))
            )
            Row(
                Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LogoMark(Modifier.size(62.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(assistantName.ifBlank { "المساعد الذكي" }, color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("مساعدتك الذكية داخل المنظومة", color = Muted, fontSize = 12.sp)
                    Text("✦  ابدأ محادثة جديدة", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("›", color = Blue, fontSize = 30.sp)
            }
        }

        Surface(
            color = Color(0xFF0A1A28), shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, Blue.copy(alpha = .72f), RoundedCornerShape(30.dp))
                .clickable(onClick = openAssistant)
        ) {
            Row(
                Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("◉", color = Blue, fontSize = 19.sp)
                Text("اسألني عن أي شيء…", Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                Text("فتح المحادثة", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = Panel.copy(alpha = .94f), shape = RoundedCornerShape(19.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, GlassEdge, RoundedCornerShape(19.dp))
                .clickable(onClick = openEngines)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("▣", color = Blue, fontSize = 23.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("المحرك الحالي", color = Muted, fontSize = 11.sp)
                    Text(selectedEngine.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(engineDetail, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                Surface(
                    color = if (apiConfigured) Color(0xFF0B493B) else Color(0xFF3E3018),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(readiness, color = if (apiConfigured) Color(0xFF6DF0B6) else Gold,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("أدواتك", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("تعمل حسب الإعداد المتاح", fontSize = 10.sp, color = Muted)
        }
        features.chunked(3).forEach { rowFeatures ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowFeatures.forEach { feature ->
                    FeatureTile(feature, Modifier.weight(1f)) {
                        when (feature.title) {
                            "المحادثة" -> openAssistant()
                            "محركات الذكاء" -> openEngines()
                            "AnythingLLM" -> openAnythingLLM()
                            else -> openFeature(feature.title)
                        }
                    }
                }
                repeat(3 - rowFeatures.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Surface(
            color = Color(0xFF0B1723), shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, GlassEdge.copy(alpha = .7f), RoundedCornerShape(15.dp))
        ) {
            Text(
                "خصوصيتك بيدك • لا تُرسل الرسائل إلى المحرك إلا عند ضغط إرسال",
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusPanel(
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .shbeebGlass(radius = 17, elevation = 7)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = accent, fontSize = 14.sp)
            Column(Modifier.padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(detail, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FeatureTile(feature: Feature, modifier: Modifier, onClick: () -> Unit) {
    val iconColor = when (feature.title) {
        "الصور" -> Color(0xFFE275FF)
        "الفيديو" -> Color(0xFFB783FF)
        "ملفاتي", "المهام" -> Gold
        "محركات الذكاء" -> Color(0xFF5AE78F)
        "التحكم بالهاتف" -> Color(0xFF6ECFFF)
        else -> Blue
    }
    Box(
        modifier
            .shbeebGlass(radius = 17, elevation = 6)
            .clickable(onClick = onClick)
    ) {
        Spacer(
            Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                .background(Brush.horizontalGradient(
                    listOf(Color.Transparent, iconColor.copy(alpha = .75f), Color.Transparent)
                ))
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier.size(36.dp)
                    .background(Brush.radialGradient(
                        listOf(iconColor.copy(alpha = .20f), Color(0xFF0B1725))
                    ), CircleShape)
                    .border(1.dp, iconColor.copy(alpha = .55f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(feature.symbol, color = iconColor, fontSize = 22.sp) }
            Text(feature.title, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
            Text(feature.subtitle, color = Muted, fontSize = 9.sp,
                textAlign = TextAlign.Center, maxLines = 2, minLines = 2, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun EnginePicker(engine: ChatEngine, enabled: Boolean, onSelect: (ChatEngine) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text("المحرّك: ${engine.title}  ▾", maxLines = 1, fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ChatEngine.values().forEach { candidate ->
                DropdownMenuItem(
                    text = { Text((if (candidate == engine) "✓  " else "") + candidate.title) },
                    onClick = {
                        expanded = false
                        onSelect(candidate)
                    }
                )
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    assistantName: String,
    engine: ChatEngine,
    messages: List<ChatEntry>,
    apiConfigured: Boolean,
    busy: Boolean,
    error: String?,
    onEngineSelected: (ChatEngine) -> Unit,
    openSettings: () -> Unit,
    onSend: (String) -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }
    if (voiceMode) {
        VoiceConversationScreen(
            engine = engine,
            messages = messages,
            busy = busy,
            configured = apiConfigured,
            error = error,
            onSend = onSend,
            onClose = { voiceMode = false }
        )
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val chatKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var voiceInfo by remember { mutableStateOf("") }
    var ttsReady by remember { mutableStateOf(false) }
    val speech = remember(context) {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(speech) {
        onDispose { speech.stop(); speech.shutdown() }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                message = spoken.take(3000)
                voiceInfo = "راجع النص قبل الإرسال."
            } else voiceInfo = "لم يصل نص من خدمة التعرف على الكلام."
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        writer.write("SHBEEB AI — ${engine.title}\n\n")
                        messages.forEach { line ->
                            writer.write("${if (line.role == "user") "أنت" else engine.title}: ${line.text}\n\n")
                        }
                    }
                } ?: error("تعذّر إنشاء الملف.")
                voiceInfo = "تم تصدير المحادثة."
            }.onFailure { voiceInfo = "فشل تصدير المحادثة." }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!chatKeyboardOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                assistantName.ifBlank { "المساعد الذكي" },
                color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = openSettings) { Text("الإعدادات", fontSize = 12.sp) }
        }
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnginePicker(engine = engine, enabled = !busy, onSelect = onEngineSelected)
            Text(
                if (apiConfigured) "جاهز للإرسال" else "يحتاج إعداد",
                color = if (apiConfigured) Blue else Gold, fontSize = 12.sp
            )
        }
        if (!apiConfigured) {
            TextButton(
                onClick = openSettings, modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (engine == ChatEngine.LOCAL) "استيراد نموذج ذكاء الهاتف"
                    else if (engine == ChatEngine.OLLAMA) "إعداد عنوان خادم Ollama"
                    else "إعداد مفتاح ${engine.title} API",
                    maxLines = 1
                )
            }
        }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                Text(
                    when (engine) {
                        ChatEngine.LOCAL -> "بعد استيراد نموذج متوافق، تشتغل المحادثة داخل الهاتف دون إنترنت."
                        ChatEngine.OLLAMA -> "الاتصال مع Ollama على كمبيوتر ضمن شبكة Wi-Fi موثوقة."
                        else -> "عند الإرسال، يُشارك نص سؤالك مع ${engine.title}. قد تُحسب رسوم API."
                    },
                    color = Muted, fontSize = 12.sp
                )
            }
            messages.forEach { item ->
                Surface(
                    color = if (item.role == "user") Color(0xCC104766) else Color(0xD20D1B2A),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text(
                            if (item.role == "user") "أنت" else engine.title,
                            color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                        Text(item.text, color = Color.White, fontSize = 14.sp)
                        if (item.role == "assistant") {
                            TextButton(onClick = {
                                if (!ttsReady) {
                                    voiceInfo = "محرّك قراءة النص غير جاهز."
                                } else {
                                    val locale = Locale.forLanguageTag("ar")
                                    if (speech.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
                                        voiceInfo = "الصوت العربي غير مثبت في هاتفك."
                                    } else {
                                        speech.language = locale
                                        speech.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, "shbeeb-tts")
                                        voiceInfo = "جارٍ قراءة الرد."
                                    }
                                }
                            }) { Text("استمع للرد", fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (busy) Text("جارٍ انتظار رد ${engine.title}…", color = Blue, fontSize = 13.sp)
            if (error != null) Text(error, color = Color(0xFFFFAB9F), fontSize = 13.sp)
            if (voiceInfo.isNotBlank()) Text(voiceInfo, color = Muted, fontSize = 12.sp)
        }
        if (!chatKeyboardOpen) {
        Button(
            onClick = { voiceMode = true },
            enabled = apiConfigured && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🎙 بدء محادثة صوتية") }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدّث بالعربي")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                        .onFailure { voiceInfo = "خدمة الإملاء الصوتي غير متوفرة." }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("🎙 إملاء", fontSize = 12.sp) }
            TextButton(
                onClick = { exportLauncher.launch("shbeeb-${engine.id}-chat.txt") },
                enabled = messages.isNotEmpty() && !busy,
                modifier = Modifier.weight(1f)
            ) { Text("تصدير", fontSize = 12.sp) }
        }
        }
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= 3000) message = it },
                label = { Text("اكتب رسالتك") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                enabled = !busy
            )
            Button(
                onClick = {
                    if (message.isNotBlank()) {
                        onSend(message.trim())
                        message = ""
                    }
                },
                enabled = apiConfigured && !busy && message.isNotBlank()
            ) { Text("إرسال") }
        }
    }
}

@Composable
private fun EnginesScreen(localInstalled: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text("محركات الذكاء", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
        StatusPanel("المحرك المحلي", if (localInstalled)
            "تم استيراد ملف نموذج إلى الهاتف. تحقّق من تشغيله برد تجريبي."
            else "LiteRT-LM داخل الهاتف يحتاج استيراد نموذج .litertlm مرخّص.", Gold) {}
        StatusPanel("المحركات السحابية", "OpenAI وClaude وGemini وDeepSeek وOpenRouter. تحتاج مفاتيح مستقلة.", Muted) {}
        Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
            Text(
                "الهدف أن يعمل الحوار محلياً عند تثبيت نموذج حقيقي، مع التوسع مستقبلاً على الكمبيوتر. لا تعني هذه الصفحة أن النموذج يعمل حالياً.",
                Modifier.padding(17.dp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenGuide: () -> Unit,
    onKeysChanged: () -> Unit,
    connectionBusy: Boolean,
    connectionStatus: String,
    onCheckConnection: () -> Unit,
    onCheckRealConnection: () -> Unit,
    assistantName: String,
    engine: ChatEngine,
    cloudModel: String,
    onCloudModelChanged: (String) -> Unit,
    ollamaBase: String,
    ollamaModel: String,
    onLocalStatusChanged: () -> Unit,
    localResponseMode: LocalResponseMode,
    onLocalResponseModeChanged: (LocalResponseMode) -> Unit,
    historyEnabled: Boolean,
    onHistoryEnabledChanged: (Boolean) -> Unit,
    onDeleteHistory: () -> Unit,
     onOllamaBaseChanged: (String) -> Unit,
    onOllamaModelChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("الإعدادات", fontSize = 24.sp, color = Gold, fontWeight = FontWeight.Bold)
        Text("تخصيص المساعدة والخصوصية باللغة العربية", color = Muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.weight(1f)) {
                Text("فحص الجهاز")
            }
            OutlinedButton(onClick = onOpenGuide, modifier = Modifier.weight(1f)) {
                Text("؟ دليل الاستخدام")
            }
        }
        OutlinedTextField(
            value = assistantName,
            onValueChange = { if (it.length <= 32) onNameChanged(it) },
            label = { Text("اسم المساعدة") },
            supportingText = { Text("يُحفظ على هذا الهاتف فقط.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("المحرّك الحالي: ${engine.title}", color = Gold, fontWeight = FontWeight.Bold)
        var pendingRealCheck by remember { mutableStateOf(false) }
        if (pendingRealCheck) {
            AlertDialog(
                onDismissRequest = { pendingRealCheck = false },
                title = { Text("تأكيد اختبار التوليد الفعلي") },
                text = { Text(
                    "سيُرسل طلب نصي قصير إلى ${engine.title}. قد يستهلك من رصيد API أو الحصة المجانية. لن يُحفظ الطلب في سجل المحادثات. هل توافق؟"
                ) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRealCheck = false
                        onCheckRealConnection()
                    }) { Text("أوافق وأختبر") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRealCheck = false }) { Text("إلغاء") }
                }
            )
        }
        var pendingHistoryDelete by remember { mutableStateOf(false) }
        if (pendingHistoryDelete) {
            AlertDialog(
                onDismissRequest = { pendingHistoryDelete = false },
                title = { Text("تأكيد حذف سجل المحادثات") },
                text = { Text("هل تريد حذف جميع الرسائل المحفوظة على الجهاز لكل المحركات؟ لا يمكن استعادتها.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteHistory()
                        pendingHistoryDelete = false
                    }) { Text("حذف نهائي") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingHistoryDelete = false }) { Text("إلغاء") }
                }
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("حفظ المحادثات على الهاتف", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "اختياري، مغلق افتراضياً. تُحفظ محادثة كل محرك منفصلة دون رفعها تلقائياً. الحفظ المحلي غير مشفّر؛ إيقافه لا يحذف القديم.",
                    color = Muted, fontSize = 12.sp
                )
            }
            Switch(checked = historyEnabled, onCheckedChange = onHistoryEnabledChanged)
        }
        OutlinedButton(
            onClick = { pendingHistoryDelete = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("حذف كل سجل المحادثات المحفوظ محلياً") }
        Text("غيّر المحرك من شاشة المساعد الذكي، وهنا اضبط الاتصال الخاص بكل خدمة.",
            color = Muted, fontSize = 12.sp)

        when (engine) {
            ChatEngine.OPENAI, ChatEngine.CLAUDE, ChatEngine.GEMINI,
            ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER -> {
                ProviderKeySettings(engine = engine, onChange = onKeysChanged)
            }
            ChatEngine.LOCAL -> {
                LocalModelSettings(
                    onInstalled = onLocalStatusChanged,
                    responseMode = localResponseMode,
                    onResponseModeChanged = onLocalResponseModeChanged
                )
            }
            ChatEngine.OLLAMA -> {
                OutlinedTextField(
                    value = ollamaBase,
                    onValueChange = { if (it.length <= 180) onOllamaBaseChanged(it.trim()) },
                    label = { Text("عنوان الكمبيوتر (مثال http://192.168.1.10:11434)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ollamaModel,
                    onValueChange = { if (it.length <= 120) onOllamaModelChanged(it.trim()) },
                    label = { Text("اسم النموذج المثبت (مثال llama3.2:1b)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "يلزم تشغيل Ollama والنموذج على كمبيوتر داخل نفس شبكة Wi-Fi. الاتصال المحلي دون تشفير؛ لا تفتح منفذ Ollama للإنترنت العام. إذا كان الكمبيوتر ضعيفاً، اختر محرّكاً سحابياً.",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
        Button(
            onClick = onCheckConnection,
            enabled = !connectionBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (connectionBusy) "جارٍ اختبار المحرك…" else "فحص اتصال المحرك بدون توليد مدفوع") }
        if (engine != ChatEngine.LOCAL) {
            OutlinedButton(
                onClick = { pendingRealCheck = true },
                enabled = !connectionBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("اختبار رسالة حقيقية (قد يستهلك رصيداً)") }
        }
        if (connectionStatus.isNotBlank()) {
            Text(connectionStatus, color = Muted, fontSize = 13.sp)
        }
        if (engine != ChatEngine.OLLAMA && engine != ChatEngine.LOCAL) {
            OutlinedTextField(
                value = cloudModel,
                onValueChange = { if (it.length <= 128) onCloudModelChanged(it) },
                label = { Text("اسم نموذج ${engine.title}") },
                supportingText = {
                    Text("النموذج المقترح: ${engine.defaultModel}. بعض النماذج تحتاج صلاحية بحساب API.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "مفتاح ${engine.title} يُحفظ مشفراً داخل هذا الهاتف فقط، ولا يُعرض بعد الحفظ. احذفه من القسم أعلاه بأي وقت. نجاح فحص المفتاح لا يضمن رصيد API.",
                color = Muted, fontSize = 12.sp
            )
            Text(
                "هذه طريقة تجريبية خاصة. للنشر التجاري يجب نقل المفاتيح والطلبات إلى خادم آمن مع حسابات وحدود استخدام.",
                color = Muted, fontSize = 12.sp
            )
        }
        SettingInfo("الإصدار", "0.10.7 تجريبي — إصلاح التعرف الصوتي ورمز اللغة 12")
        SettingInfo("اللغة", "العربية — اتجاه من اليمين إلى اليسار")
        SettingInfo("البيانات", "لا ترسل رسالة حتى تضغط إرسال. عند المحركات السحابية تُرسل الرسالة وسياقها للمزوّد المحدد.")
        SettingInfo("الصلاحيات", "لا يوجد استماع دائم أو تحكم خفي؛ الإملاء يدوي عبر خدمة النظام، والمشاركة تتطلب تأكيداً")
        Text("المحادثة المحلية تحتاج استيراد نموذج متوافق؛ حفظ الرسائل اختياري، وجودة الصوت والنموذج تحتاج اختباراً على جهازك.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingInfo(title: String, detail: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Panel,
        modifier = Modifier.border(1.dp, GlassEdge, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeatureScreen(title: String) {
    Column(
        Modifier.fillMaxSize().padding(23.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(title, fontSize = 27.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Text(
                "هذا القسم محجوز للتطوير في مرحلة لاحقة. سيتم فصل أدواته وصلاحياته عن بقية التطبيق واختبارها قبل تفعيلها.",
                Modifier.padding(22.dp),
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BottomBar(selected: String, navigate: (String) -> Unit) {
    val tabs = listOf(
        Triple("home", "الرئيسية", "⌂"),
        Triple("assistant", "المساعدة", "◉"),
        Triple("engines", "المحركات", "⌘"),
        Triple("settings", "الإعدادات", "⚙")
    )
    Row(
        Modifier.fillMaxWidth()
            .shbeebGlass(radius = 21, elevation = 12)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { (key, name, symbol) ->
            val active = selected == key || (selected == "feature" && key == "home") || (selected == "diagnostics" && key == "settings")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .background(if (active) Color(0xFF0A344A) else Color.Transparent)
                    .border(if (active) 1.dp else 0.dp, if (active) Blue.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { navigate(key) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(symbol, fontSize = 20.sp, color = if (active) Blue else Muted)
                Text(name, fontSize = 11.sp, color = if (active) Color.White else Muted)
            }
        }
    }
}
