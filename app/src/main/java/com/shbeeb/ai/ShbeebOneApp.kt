package com.shbeeb.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val OneInk = Color(0xFF172723)
internal val OneMuted = Color(0xFF64736D)
internal val OnePrimary = Color(0xFF17675B)
internal val OnePrimaryDark = Color(0xFF104C43)
internal val OneMint = Color(0xFFE8F3EE)
internal val OneCanvas = Color(0xFFF5F7F4)
internal val OneSurface = Color(0xFFFFFFFF)
internal val OneBorder = Color(0xFFE1E9E4)
internal val OneWarm = Color(0xFFF9F1E6)
internal val OneAmber = Color(0xFFB36C2D)
internal val OneError = Color(0xFFB33B34)

@Composable
internal fun ShbeebOneApp() {
    val context = LocalContext.current
    val uiHandler = remember { Handler(Looper.getMainLooper()) }
    val preferences = remember(context) {
        context.getSharedPreferences("shbeeb_settings", Context.MODE_PRIVATE)
    }
    val secureApiKeys = remember(context) { SecureApiKeys(context) }
    val chatStore = remember(context) { ChatHistoryStore(context) }

    var screen by rememberSaveable { mutableStateOf("assistant") }
    var chatBusy by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var apiKeyRevision by remember { mutableStateOf(0) }
    var connectionStatus by remember { mutableStateOf("") }
    var connectionBusy by remember { mutableStateOf(false) }
    var localModelInstalled by remember(context) {
        mutableStateOf(LocalModelFiles.isInstalled(context))
    }
    var engineId by rememberSaveable {
        mutableStateOf(preferences.getString("selected_engine", ChatEngine.LOCAL.id) ?: ChatEngine.LOCAL.id)
    }
    var historyEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("history_enabled", false))
    }
    val chatMessages = remember(context) {
        mutableStateListOf<ChatEntry>().apply {
            if (historyEnabled) addAll(chatStore.load(engineId))
        }
    }
    var assistantName by rememberSaveable {
        mutableStateOf(preferences.getString("assistant_name", "شبيب") ?: "شبيب")
    }
    var openAiModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_openai", ChatEngine.OPENAI.defaultModel) ?: ChatEngine.OPENAI.defaultModel)
    }
    var claudeModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_claude", ChatEngine.CLAUDE.defaultModel) ?: ChatEngine.CLAUDE.defaultModel)
    }
    var geminiModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_gemini", ChatEngine.GEMINI.defaultModel) ?: ChatEngine.GEMINI.defaultModel)
    }
    var deepSeekModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_deepseek", ChatEngine.DEEPSEEK.defaultModel) ?: ChatEngine.DEEPSEEK.defaultModel)
    }
    var openRouterModel by rememberSaveable {
        mutableStateOf(preferences.getString("model_openrouter", ChatEngine.OPENROUTER.defaultModel) ?: ChatEngine.OPENROUTER.defaultModel)
    }
    var ollamaBase by rememberSaveable {
        mutableStateOf(preferences.getString("ollama_host", "") ?: "")
    }
    var ollamaModel by rememberSaveable {
        mutableStateOf(preferences.getString("ollama_model", "llama3.2:1b") ?: "llama3.2:1b")
    }
    var localResponseModeId by rememberSaveable {
        mutableStateOf(preferences.getString("local_response_mode", LocalResponseMode.BALANCED.id) ?: LocalResponseMode.BALANCED.id)
    }

    val engine = ChatEngine.fromId(engineId)
    val cloudKeyReady = remember(engine, apiKeyRevision) {
        when (engine) {
            ChatEngine.OPENAI, ChatEngine.GEMINI, ChatEngine.CLAUDE,
            ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER -> secureApiKeys.has(engine)
            ChatEngine.LOCAL, ChatEngine.OLLAMA -> false
        }
    }
    val cloudModel = when (engine) {
        ChatEngine.OPENAI -> openAiModel
        ChatEngine.CLAUDE -> claudeModel
        ChatEngine.GEMINI -> geminiModel
        ChatEngine.DEEPSEEK -> deepSeekModel
        ChatEngine.OPENROUTER -> openRouterModel
        ChatEngine.OLLAMA, ChatEngine.LOCAL -> ""
    }
    val readyForAttempt = when (engine) {
        ChatEngine.LOCAL -> localModelInstalled
        ChatEngine.OLLAMA -> ollamaBase.isNotBlank() && ollamaModel.isNotBlank()
        else -> cloudKeyReady
    }

    val selectEngine: (ChatEngine) -> Unit = { selected ->
        if (!chatBusy && selected != engine) {
            engineId = selected.id
            preferences.edit().putString("selected_engine", selected.id).apply()
            chatMessages.clear()
            if (historyEnabled) chatMessages.addAll(chatStore.load(selected.id))
            chatError = null
            connectionStatus = ""
        }
    }

    val sendPrompt: (String) -> Unit = { question ->
        val clean = question.trim().take(3000)
        if (!chatBusy && readyForAttempt && clean.isNotBlank()) {
            val userEntry = ChatEntry("user", clean)
            chatMessages.add(userEntry)
            if (historyEnabled) runCatching { chatStore.append(engineId, userEntry) }
                .onFailure { chatError = "تعذّر حفظ الرسالة على الهاتف." }
            val history = chatMessages.takeLast(12).toList()
            val engineForRequest = engine
            val localMode = LocalResponseMode.fromId(localResponseModeId)
            val ollamaHost = ollamaBase
            val selectedOllamaModel = ollamaModel
            val selectedCloudModel = cloudModel
            chatError = null
            chatBusy = true
            Thread {
                val result = runCatching {
                    if (engineForRequest == ChatEngine.LOCAL) {
                        LocalOnDeviceEngine.reply(context, clean, localMode)
                    } else {
                        val key = if (engineForRequest == ChatEngine.OLLAMA) ""
                            else secureApiKeys.read(engineForRequest)
                        ChatTransport.reply(
                            engineForRequest, key, history,
                            ollamaHost, selectedOllamaModel, selectedCloudModel
                        )
                    }
                }
                uiHandler.post {
                    result.onSuccess { answer ->
                        val assistantEntry = ChatEntry("assistant", answer)
                        chatMessages.add(assistantEntry)
                        if (historyEnabled) runCatching { chatStore.append(engineForRequest.id, assistantEntry) }
                            .onFailure { chatError = "وصل الرد لكن تعذّر حفظه محلياً." }
                    }.onFailure { failure ->
                        chatError = failure.message ?: "تعذّر إكمال الطلب."
                    }
                    chatBusy = false
                }
            }.start()
        }
    }

    val onCloudModelChanged: (String) -> Unit = { value ->
        when (engine) {
            ChatEngine.OPENAI -> { openAiModel = value; preferences.edit().putString("model_openai", value).apply() }
            ChatEngine.CLAUDE -> { claudeModel = value; preferences.edit().putString("model_claude", value).apply() }
            ChatEngine.GEMINI -> { geminiModel = value; preferences.edit().putString("model_gemini", value).apply() }
            ChatEngine.DEEPSEEK -> { deepSeekModel = value; preferences.edit().putString("model_deepseek", value).apply() }
            ChatEngine.OPENROUTER -> { openRouterModel = value; preferences.edit().putString("model_openrouter", value).apply() }
            ChatEngine.LOCAL, ChatEngine.OLLAMA -> Unit
        }
    }

    val checkConnection: () -> Unit = {
        if (!connectionBusy) {
            val engineToCheck = engine
            val modelToCheck = cloudModel
            val hostToCheck = ollamaBase
            val ollamaModelToCheck = ollamaModel
            connectionBusy = true
            connectionStatus = "جارٍ فحص إعداد الاتصال فقط…"
            Thread {
                val result = runCatching {
                    val key = when (engineToCheck) {
                        ChatEngine.OPENAI, ChatEngine.CLAUDE, ChatEngine.GEMINI,
                        ChatEngine.DEEPSEEK, ChatEngine.OPENROUTER -> secureApiKeys.read(engineToCheck)
                        else -> ""
                    }
                    ProviderConnectionProbe.check(context, engineToCheck, key, modelToCheck, hostToCheck, ollamaModelToCheck)
                }
                uiHandler.post {
                    connectionStatus = result.getOrNull()?.message ?: "فشل فحص الإعداد. راجع عنوان المحرك أو بيانات الحساب."
                    connectionBusy = false
                }
            }.start()
        }
    }

    val checkRealConnection: () -> Unit = {
        if (!connectionBusy && engine != ChatEngine.LOCAL) {
            val selectedEngine = engine
            val model = cloudModel
            val host = ollamaBase
            val localOllamaModel = ollamaModel
            connectionBusy = true
            connectionStatus = "جارٍ إرسال اختبار قصير بعد موافقتك…"
            Thread {
                val result = runCatching {
                    val key = if (selectedEngine == ChatEngine.OLLAMA) "" else secureApiKeys.read(selectedEngine)
                    ChatTransport.reply(
                        selectedEngine, key, listOf(ChatEntry("user", "أجب بكلمة واحدة: جاهز")),
                        host, localOllamaModel, model, diagnostic = true
                    )
                }
                uiHandler.post {
                    connectionStatus = result.fold(
                        onSuccess = { "نجح الاختبار الحقيقي عبر ${selectedEngine.title}." },
                        onFailure = { it.message ?: "فشل اختبار التوليد." }
                    )
                    connectionBusy = false
                }
            }.start()
        }
    }

    val onOpenDiagnostics: () -> Unit = { screen = "diagnostics" }
    val onOpenGuide: () -> Unit = { screen = "guide" }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = OnePrimary,
            onPrimary = Color.White,
            secondary = OneAmber,
            onSecondary = Color.White,
            background = OneCanvas,
            onBackground = OneInk,
            surface = OneSurface,
            onSurface = OneInk,
            surfaceVariant = OneMint,
            onSurfaceVariant = OneMuted,
            outline = OneBorder,
            error = OneError
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ShbeebOneShell(screen = screen, engine = engine, ready = readyForAttempt, onNavigate = { screen = it }) {
                when (screen) {
                    "assistant" -> OneAssistantScreen(
                        assistantName = assistantName,
                        engine = engine,
                        messages = chatMessages,
                        ready = readyForAttempt,
                        busy = chatBusy,
                        error = chatError,
                        onEngineSelected = selectEngine,
                        onOpenSettings = { screen = "settings" },
                        onSend = sendPrompt
                    )
                    "studio" -> OneStudioScreen()
                    "tools" -> OneToolsHub(
                        onOpenFiles = { screen = "files" },
                        onOpenTasks = { screen = "tasks" },
                        onOpenDeviceTools = { screen = "device" }
                    )
                    "files" -> FilesScreen()
                    "tasks" -> TasksScreen()
                    "device" -> SafeDeviceScreen()
                    "settings" -> OneSettingsScreen(
                        assistantName = assistantName,
                        onNameChanged = { name ->
                            assistantName = name
                            preferences.edit().putString("assistant_name", name).apply()
                        },
                        engine = engine,
                        onEngineSelected = selectEngine,
                        cloudModel = cloudModel,
                        onCloudModelChanged = onCloudModelChanged,
                        ollamaBase = ollamaBase,
                        onOllamaBaseChanged = { ollamaBase = it; preferences.edit().putString("ollama_host", it).apply() },
                        ollamaModel = ollamaModel,
                        onOllamaModelChanged = { ollamaModel = it; preferences.edit().putString("ollama_model", it).apply() },
                        onKeysChanged = { apiKeyRevision += 1 },
                        localResponseMode = LocalResponseMode.fromId(localResponseModeId),
                        onLocalResponseModeChanged = { mode ->
                            localResponseModeId = mode.id
                            preferences.edit().putString("local_response_mode", mode.id).apply()
                        },
                        onLocalStatusChanged = { localModelInstalled = LocalModelFiles.isInstalled(context) },
                        connectionBusy = connectionBusy,
                        connectionStatus = connectionStatus,
                        onCheckConnection = checkConnection,
                        onCheckRealConnection = checkRealConnection,
                        historyEnabled = historyEnabled,
                        onHistoryEnabledChanged = { enabled ->
                            if (enabled && !historyEnabled) {
                                chatMessages.forEach { entry -> runCatching { chatStore.append(engine.id, entry) } }
                            }
                            historyEnabled = enabled
                            preferences.edit().putBoolean("history_enabled", enabled).apply()
                        },
                        onDeleteHistory = {
                            runCatching { chatStore.deleteAll() }
                            chatMessages.clear()
                        },
                        onOpenDiagnostics = onOpenDiagnostics,
                        onOpenGuide = onOpenGuide
                    )
                    "diagnostics" -> DeviceDiagnosticsScreen()
                    "guide" -> UserGuideScreen()
                    else -> OneToolsHub(
                        onOpenFiles = { screen = "files" },
                        onOpenTasks = { screen = "tasks" },
                        onOpenDeviceTools = { screen = "device" }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShbeebOneShell(
    screen: String,
    engine: ChatEngine,
    ready: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val mainRoute = screen in setOf("assistant", "studio", "tools", "settings")
    val parentRoute = when (screen) {
        "files", "tasks", "device" -> "tools"
        "diagnostics", "guide" -> "settings"
        else -> screen
    }
    val title = when (screen) {
        "files" -> "الملفات"
        "tasks" -> "مهامي"
        "device" -> "أدوات الهاتف"
        "diagnostics" -> "فحص الجهاز"
        "guide" -> "دليل الاستخدام"
        "assistant" -> ""
        "studio" -> "الإنشاء"
        "tools" -> "الأدوات"
        "settings" -> "الإعدادات"
        else -> ""
    }

    Column(
        Modifier.fillMaxSize()
            .background(OneCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (mainRoute) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(39.dp).clip(RoundedCornerShape(14.dp)).background(OnePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("Shbeeb One", color = OneInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("مساعدك الذكي", color = OneMuted, fontSize = 11.sp)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    IconButton(onClick = { onNavigate(parentRoute) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = OneInk)
                    }
                    Text(title, color = OneInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            if (screen != "settings" && screen != "diagnostics" && screen != "guide") {
                Surface(
                    modifier = Modifier.clip(CircleShape).clickable { onNavigate("settings") },
                    color = if (ready) OneMint else OneWarm,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (ready) OneBorder else Color(0xFFEEDCC8))
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (ready) OnePrimary else OneAmber))
                        Text(
                            when (engine) {
                                ChatEngine.LOCAL -> if (ready) "LiteRT · محلي" else "LiteRT · اختر نموذجاً"
                                ChatEngine.OLLAMA -> "Ollama · شبكة محلية"
                                else -> engine.title
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (ready) OnePrimaryDark else OneAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) { content() }

        if (!keyboardVisible && mainRoute) OneBottomBar(screen, onNavigate)
    }
}

@Composable
private fun OneBottomBar(selected: String, onNavigate: (String) -> Unit) {
    val entries = listOf(
        Triple("assistant", "المساعد", Icons.Filled.ChatBubbleOutline),
        Triple("studio", "إنشاء", Icons.Filled.AutoAwesome),
        Triple("tools", "الأدوات", Icons.Filled.Apps),
        Triple("settings", "الإعدادات", Icons.Filled.Settings)
    )
    Surface(color = OneSurface, shadowElevation = 9.dp, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            entries.forEach { (route, label, icon) ->
                val active = selected == route
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (active) OneMint else Color.Transparent)
                        .clickable { onNavigate(route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(icon, contentDescription = label, tint = if (active) OnePrimary else OneMuted, modifier = Modifier.size(21.dp))
                    Text(label, color = if (active) OnePrimaryDark else OneMuted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun OneAssistantScreen(
    assistantName: String,
    engine: ChatEngine,
    messages: List<ChatEntry>,
    ready: Boolean,
    busy: Boolean,
    error: String?,
    onEngineSelected: (ChatEngine) -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }
    var engineMenu by remember { mutableStateOf(false) }
    var pendingCloudText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    if (voiceMode) {
        VoiceConversationScreen(
            engine = engine,
            messages = messages,
            busy = busy,
            configured = ready,
            error = error,
            onSend = onSend,
            onClose = { voiceMode = false }
        )
        return
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val requestSend: () -> Unit = {
        val value = draft.trim()
        if (value.isNotBlank() && ready && !busy) {
            if (engine != ChatEngine.LOCAL && engine != ChatEngine.OLLAMA) pendingCloudText = value
            else { onSend(value); draft = "" }
        }
    }

    if (pendingCloudText != null) {
        AlertDialog(
            onDismissRequest = { pendingCloudText = null },
            title = { Text("تأكيد الإرسال إلى ${engine.title}") },
            text = {
                Text("سيُرسل سؤالك مع جزء من سياق هذه المحادثة إلى ${engine.title}. قد يستهلك ذلك رصيد API أو يترتب عليه رسم حسب حسابك. لن يتم الإرسال قبل موافقتك.")
            },
            confirmButton = {
                Button(onClick = {
                    val prompt = pendingCloudText
                    pendingCloudText = null
                    if (!prompt.isNullOrBlank()) { onSend(prompt); draft = "" }
                }) { Text("أوافق وأرسل") }
            },
            dismissButton = { TextButton(onClick = { pendingCloudText = null }) { Text("إلغاء") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("${assistantName.ifBlank { "شبيب" }} معك", color = OneInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        engine == ChatEngine.LOCAL -> if (ready) "المعالجة على الهاتف" else "اختر نموذج LiteRT من الإعدادات للبدء"
                        engine == ChatEngine.OLLAMA -> "اتصال بجهازك عبر الشبكة المحلية"
                        else -> "سحابي · الإرسال بعد تأكيدك لكل رسالة"
                    },
                    color = OneMuted,
                    fontSize = 11.sp
                )
            }
            Box {
                OutlinedButton(
                    onClick = { engineMenu = true },
                    enabled = !busy,
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(engine.title, color = OnePrimaryDark, fontSize = 12.sp, maxLines = 1)
                }
                DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                    ChatEngine.values().forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(if (item == ChatEngine.LOCAL) "Gemma / LiteRT (محلي)" else item.title, fontWeight = FontWeight.SemiBold)
                                    Text(item.hint, color = OneMuted, fontSize = 11.sp)
                                }
                            },
                            leadingIcon = { if (item == engine) Icon(Icons.Filled.Check, contentDescription = null, tint = OnePrimary) },
                            onClick = { engineMenu = false; onEngineSelected(item) }
                        )
                    }
                }
            }
        }

        if (!ready) {
            OneInfoCard(
                title = if (engine == ChatEngine.LOCAL) "ابدأ بالذكاء المحلي" else if (engine == ChatEngine.OLLAMA) "أكمل إعداد الاتصال" else "أكمل إعداد المحرك",
                body = when (engine) {
                    ChatEngine.LOCAL -> "اختر نموذج محادثة متوافقاً بصيغة .litertlm. يحتفظ التطبيق بنسخة تشغيل داخلية لتوافق LiteRT-LM ويحتاج مساحة إضافية بحجم الملف تقريباً."
                    ChatEngine.OLLAMA -> "أدخل عنوان خادم Ollama واسم نموذج مثبّت على كمبيوتر في شبكة Wi‑Fi موثوقة."
                    else -> "احفظ مفتاح API الخاص بـ${engine.title} من الإعدادات. ستحتاج تأكيد الإرسال قبل كل طلب سحابي."
                },
                actionLabel = "فتح الإعدادات",
                onAction = onOpenSettings
            )
        }

        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Text("مساحة هادئة للتفكير والعمل", color = OnePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("كيف أقدر أساعدك اليوم؟", color = OneInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("اسأل، خطّط، اكتب، أو حوّل فكرتك إلى صورة من الاستوديو.", color = OneMuted, fontSize = 14.sp)
                Spacer(Modifier.height(19.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OneSuggestion("اكتب لي خطة واضحة لمهمتي القادمة") { draft = it }
                    OneSuggestion("لخّص هذا الموضوع في نقاط سهلة") { draft = it }
                    OneSuggestion("ساعدني على ترتيب أفكاري خطوة بخطوة") { draft = it }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 9.dp)
            ) {
                itemsIndexed(messages) { _, entry -> OneMessageBubble(entry) }
                if (busy) item {
                    Surface(color = OneMint, shape = RoundedCornerShape(16.dp)) {
                        Text("شبيب يفكر…", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = OnePrimaryDark, fontSize = 13.sp)
                    }
                }
                if (!error.isNullOrBlank()) item { OneErrorCard(error) }
            }
        }

        if (!error.isNullOrBlank() && messages.isEmpty()) OneErrorCard(error)

        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (ready && !busy) voiceMode = true else onOpenSettings() },
                enabled = !busy,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(OneMint)
            ) { Icon(Icons.Filled.Mic, contentDescription = "محادثة صوتية", tint = OnePrimary) }
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 3000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالتك…") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                enabled = !busy
            )
            IconButton(
                onClick = requestSend,
                enabled = ready && !busy && draft.isNotBlank(),
                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (ready && !busy && draft.isNotBlank()) OnePrimary else OneBorder)
            ) { Icon(Icons.Filled.Send, contentDescription = "إرسال", tint = Color.White) }
        }
        Text(
            if (engine == ChatEngine.LOCAL) "المحادثة محلية بعد تجهيز النموذج. جودة الرد تعتمد على النموذج المثبت."
            else if (engine == ChatEngine.OLLAMA) "تُرسل الرسائل إلى خادمك المحلي على الشبكة."
            else "لا يُرسل شيء إلى السحابة دون تأكيدك. قد تنطبق رسوم حساب المزوّد.",
            Modifier.fillMaxWidth().padding(bottom = 3.dp),
            color = OneMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OneMessageBubble(entry: ChatEntry) {
    val user = entry.role == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (user) Alignment.Start else Alignment.End
    ) {
        Text(if (user) "أنت" else "شبيب", color = OneMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(.92f),
            color = if (user) OnePrimary else OneSurface,
            contentColor = if (user) Color.White else OneInk,
            shape = RoundedCornerShape(20.dp),
            border = if (user) null else BorderStroke(1.dp, OneBorder),
            shadowElevation = if (user) 0.dp else 1.dp
        ) {
            Text(entry.text, Modifier.padding(horizontal = 15.dp, vertical = 12.dp), fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun OneSuggestion(label: String, onClick: (String) -> Unit) {
    OutlinedButton(
        onClick = { onClick(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OneBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OneInk)
    ) {
        Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontSize = 13.sp)
    }
}

@Composable
private fun OneInfoCard(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = OneWarm,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFEEDCC8))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = OneAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, color = OneInk, fontSize = 12.sp, lineHeight = 18.sp)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = OnePrimaryDark) }
            }
        }
    }
}

@Composable
private fun OneErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        color = Color(0xFFFFF0EE),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF2D1CC))
    ) { Text(message, Modifier.padding(12.dp), color = OneError, fontSize = 12.sp) }
}

@Composable
private fun OneStudioScreen() {
    var mode by rememberSaveable { mutableStateOf("images") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("استوديو محلي", color = OneInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("صمّم صورة على الهاتف، أو حوّل صورة إلى مقطع MP4. لا يوجد إرسال تلقائي لأي خدمة سحابية.", color = OneMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == "images") Button(onClick = { mode = "images" }, modifier = Modifier.weight(1f)) { Text("توليد الصور") }
            else OutlinedButton(onClick = { mode = "images" }, modifier = Modifier.weight(1f)) { Text("توليد الصور") }
            if (mode == "video") Button(onClick = { mode = "video" }, modifier = Modifier.weight(1f)) { Text("فيديو من صورة") }
            else OutlinedButton(onClick = { mode = "video" }, modifier = Modifier.weight(1f)) { Text("فيديو من صورة") }
        }
        if (mode == "images") {
            OneInfoCard(
                title = "Stable Diffusion 1.5 على الجهاز",
                body = "توليد محلي بعد اختيار نموذج SD 1.5 (حوالي 1.57 GB). النتائج بدقة 512×512، والأداء والحرارة يحتاجان اختباراً على هاتفك. هذا نموذج صور وليس محرك محادثة."
            )
            LocalImageGenerationControls(promptSource = "")
        } else {
            OneInfoCard(
                title = "المتاح حالياً: صورة إلى MP4",
                body = "ينشئ التطبيق مقطعاً صامتاً عمودياً مدته 8 ثوانٍ من صورة تختارها، مع حركة كاميرا بسيطة. هذا ليس توليد فيديو AI من نص ولا تحريكاً للشخصيات."
            )
            VideoMakerControls()
        }
    }
}

@Composable
private fun OneToolsHub(onOpenFiles: () -> Unit, onOpenTasks: () -> Unit, onOpenDeviceTools: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("أدواتك اليومية", color = OneInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("الأدوات لا تنفّذ أوامر أو تغيّر شيئاً من تلقاء نفسها.", color = OneMuted, fontSize = 13.sp)
        OneToolCard("ملفاتي", "معاينة محلية محدودة لملفات النص دون رفعها إلى الذكاء الاصطناعي.", Icons.Filled.FolderOpen, onOpenFiles)
        OneToolCard("المهام", "قائمة بسيطة محفوظة على الهاتف؛ لا تعمل في الخلفية ولا ترسل تنبيهات.", Icons.Filled.Check, onOpenTasks)
        OneToolCard("التحكم الآمن", "فتح إعدادات النظام أو لوحة الاتصال أو نافذة المشاركة بعد تأكيدك.", Icons.Filled.Build, onOpenDeviceTools)
        OneInfoCard(
            title = "تكامل AnythingLLM",
            body = "مكتبة GGUF المشتركة محفوظة كما هي، لكنها ليست صيغة LiteRT/Gemma. أخفيت فتح واجهة AnythingLLM المدمجة مؤقتاً لأن دخولها كان يغلق التطبيق؛ لن أعرّض التطبيق للانهيار قبل تحديد الخطأ."
        )
    }
}

@Composable
private fun OneToolCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OneSurface),
        border = BorderStroke(1.dp, OneBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(OneMint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = OnePrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = OneInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(description, color = OneMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun OneSettingsScreen(
    assistantName: String,
    onNameChanged: (String) -> Unit,
    engine: ChatEngine,
    onEngineSelected: (ChatEngine) -> Unit,
    cloudModel: String,
    onCloudModelChanged: (String) -> Unit,
    ollamaBase: String,
    onOllamaBaseChanged: (String) -> Unit,
    ollamaModel: String,
    onOllamaModelChanged: (String) -> Unit,
    onKeysChanged: () -> Unit,
    localResponseMode: LocalResponseMode,
    onLocalResponseModeChanged: (LocalResponseMode) -> Unit,
    onLocalStatusChanged: () -> Unit,
    connectionBusy: Boolean,
    connectionStatus: String,
    onCheckConnection: () -> Unit,
    onCheckRealConnection: () -> Unit,
    historyEnabled: Boolean,
    onHistoryEnabledChanged: (Boolean) -> Unit,
    onDeleteHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGuide: () -> Unit
) {
    var pendingRealTest by remember { mutableStateOf(false) }
    var pendingHistoryDelete by remember { mutableStateOf(false) }

    if (pendingRealTest) {
        AlertDialog(
            onDismissRequest = { pendingRealTest = false },
            title = { Text("تأكيد اختبار التوليد الحقيقي") },
            text = { Text("سيُرسل اختبار قصير إلى ${engine.title}. قد يستهلك رصيد API أو حصة الاستخدام. لن يبدأ الاختبار دون موافقتك.") },
            confirmButton = { Button(onClick = { pendingRealTest = false; onCheckRealConnection() }) { Text("أوافق وأختبر") } },
            dismissButton = { TextButton(onClick = { pendingRealTest = false }) { Text("إلغاء") } }
        )
    }
    if (pendingHistoryDelete) {
        AlertDialog(
            onDismissRequest = { pendingHistoryDelete = false },
            title = { Text("حذف كل المحادثات المحفوظة؟") },
            text = { Text("سيُحذف السجل المحلي لكل المحركات من هذا الهاتف. لا يمكن التراجع عن ذلك.") },
            confirmButton = { Button(onClick = { onDeleteHistory(); pendingHistoryDelete = false }) { Text("حذف نهائي") } },
            dismissButton = { TextButton(onClick = { pendingHistoryDelete = false }) { Text("إلغاء") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("إعدادات المساعد", color = OneInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("اختَر محركك، واضبط الخصوصية والاتصال من مكان واحد.", color = OneMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("فحص الجهاز") }
            OutlinedButton(onClick = onOpenGuide, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("دليل الاستخدام") }
        }

        OneSectionTitle("التخصيص")
        OutlinedTextField(
            value = assistantName,
            onValueChange = { if (it.length <= 32) onNameChanged(it) },
            label = { Text("اسم المساعد") },
            supportingText = { Text("يُحفظ على هذا الهاتف فقط.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OneSectionTitle("اختيار محرك الذكاء")
        ChatEngine.values().forEach { option ->
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onEngineSelected(option) },
                color = if (option == engine) OneMint else OneSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (option == engine) OnePrimary.copy(alpha = .35f) else OneBorder)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option == engine, onClick = { onEngineSelected(option) })
                    Column(Modifier.weight(1f)) {
                        Text(if (option == ChatEngine.LOCAL) "Gemma / LiteRT — على الهاتف" else option.title, color = OneInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(option.hint, color = OneMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        OneSectionTitle("إعداد ${if (engine == ChatEngine.LOCAL) "النموذج المحلي" else engine.title}")
        when (engine) {
            ChatEngine.LOCAL -> LocalModelSettings(
                onInstalled = onLocalStatusChanged,
                responseMode = localResponseMode,
                onResponseModeChanged = onLocalResponseModeChanged
            )
            ChatEngine.OLLAMA -> {
                OutlinedTextField(
                    value = ollamaBase,
                    onValueChange = { if (it.length <= 180) onOllamaBaseChanged(it.trim()) },
                    label = { Text("عنوان Ollama على الكمبيوتر") },
                    supportingText = { Text("مثال: http://192.168.1.10:11434 — شبكة Wi‑Fi موثوقة فقط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ollamaModel,
                    onValueChange = { if (it.length <= 120) onOllamaModelChanged(it.trim()) },
                    label = { Text("اسم النموذج المثبّت") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OneInfoCard(title = "اتصال محلي بالشبكة", body = "لا تفتح خادم Ollama للإنترنت العام. هذه الرسائل تذهب إلى الكمبيوتر الذي أدخلت عنوانه.")
            }
            else -> {
                ProviderKeySettings(engine = engine, onChange = onKeysChanged)
                OutlinedTextField(
                    value = cloudModel,
                    onValueChange = { if (it.length <= 128) onCloudModelChanged(it) },
                    label = { Text("اسم نموذج ${engine.title}") },
                    supportingText = { Text("المقترح: ${engine.defaultModel}. يعتمد التوفر والرسوم على حساب المزوّد.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OneInfoCard(title = "خصوصية السحابة", body = "لا تُرسل الرسائل دون تأكيد داخل المحادثة. عند الموافقة يشارك التطبيق النص وسياقاً حديثاً مع المزوّد المحدد.")
            }
        }

        Button(onClick = onCheckConnection, enabled = !connectionBusy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
            Text(if (connectionBusy) "جارٍ فحص الإعداد…" else "فحص الاتصال دون إنشاء رد")
        }
        if (engine != ChatEngine.LOCAL) {
            OutlinedButton(
                onClick = { pendingRealTest = true },
                enabled = !connectionBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) { Text("اختبار توليد حقيقي — قد يستهلك رصيداً") }
        }
        if (connectionStatus.isNotBlank()) OneInfoCard(title = "نتيجة الفحص", body = connectionStatus)

        OneSectionTitle("الخصوصية وسجل المحادثات")
        Surface(color = OneSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, OneBorder)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("حفظ المحادثات على الهاتف", color = OneInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("اختياري ومغلق افتراضياً. السجل المحلي غير مشفر تشفيراً خاصاً.", color = OneMuted, fontSize = 11.sp)
                }
                Switch(checked = historyEnabled, onCheckedChange = onHistoryEnabledChanged)
            }
        }
        OutlinedButton(onClick = { pendingHistoryDelete = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("حذف كل السجل المحلي") }

        OneSectionTitle("التكاملات")
        OneInfoCard(
            title = "AnythingLLM وملفات GGUF",
            body = "مكتبة GGUF المشتركة باقية، لكن صيغة GGUF مختلفة عن نموذج Gemma بصيغة .litertlm. واجهة AnythingLLM المدمجة مخفية مؤقتاً لأن فتحها كان يسبب إغلاق التطبيق؛ نحتاج سجل انهيار من الجهاز قبل إعادتها إلى التنقل."
        )
        OneInfoCard(
            title = "معلومات مهمة",
            body = "المعالجة المحلية تعمل فقط عند تجهيز ملف نموذج متوافق. عند اختيار محرك سحابي قد تُرسل الرسالة بعد موافقتك وقد تُحتسب تكلفة لدى المزوّد. الصوت والفيديو المحليان محدودان بالميزات الموضحة في الاستوديو."
        )
    }
}

@Composable
private fun OneSectionTitle(title: String) {
    Text(title, color = OnePrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
}
