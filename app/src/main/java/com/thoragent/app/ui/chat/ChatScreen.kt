package com.thoragent.app.ui.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.thoragent.app.MainActivity
import com.thoragent.app.data.db.MessageEntity
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorBg
import com.thoragent.app.ui.theme.ThorErr
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorSurface
import com.thoragent.app.ui.theme.ThorSurfaceHigh
import com.thoragent.app.ui.theme.ThorTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    nav: NavHostController,
    showBar: Boolean = false,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val messages by vm.messages.collectAsState()
    val streaming by vm.streamingText.collectAsState()
    val busy by vm.busy.collectAsState()
    val waitingFirstToken by vm.waitingFirstToken.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val sessionId by vm.sessionId.collectAsState()
    val providerInfo by vm.providerInfo.collectAsState()
    val approval by vm.approval.collectAsState()
    val toast by vm.toast.collectAsState(initial = "")
    val goal by vm.goal.collectAsState()
    val usage by vm.usage.collectAsState()
    val sessionModel by vm.sessionModel.collectAsState()
    val effectiveModel by vm.effectiveModel.collectAsState()
    val prefill by vm.prefill.collectAsState()
    val slashCommands by vm.slashCommands.collectAsState()

    // v0.4.0 — preferensi UI hidup
    val timestamps by vm.timestamps.collectAsState()
    val focusMode by vm.focusMode.collectAsState()
    val verboseMode by vm.verboseMode.collectAsState()
    val statusbarOn by vm.statusbarOn.collectAsState()
    val batteryOn by vm.batteryOn.collectAsState()
    val petName by vm.petName.collectAsState()
    val queue by vm.queue.collectAsState()
    val bgJobs by vm.bgJobs.collectAsState()
    val attachment by vm.attachment.collectAsState()
    val pausedNow by vm.pausedNow.collectAsState()
    val insert by vm.insertText.collectAsState()

    val gold = MaterialTheme.colorScheme.primary // skin-aware (/skin)
    val amber = MaterialTheme.colorScheme.secondary

    var input by remember { mutableStateOf("") }
    var showSessions by remember { mutableStateOf(false) }
    var showSlashPalette by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var unread by remember { mutableIntStateOf(0) }
    var batteryPct by remember { mutableStateOf(-1 to false) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val composerFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.attachImage(it) }
    }
    val importProfile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importProfile(it) }
    }

    fun shareFile(path: String, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Bagikan"))
        }
    }

    // prefill dari /undo /goal draft /title dll
    LaunchedEffect(prefill) {
        if (prefill.isNotBlank()) {
            input = prefill
            vm.clearPrefill()
        }
    }
    // sisipan teks (/paste) — menambah di akhir, tidak menimpa
    LaunchedEffect(insert) {
        if (insert.isNotBlank()) {
            input = if (input.isBlank()) insert else "$input\n$insert"
            vm.consumeInsert()
        }
    }
    // aksi UI dari slash command (semua 105 perintah v0.4.0)
    LaunchedEffect(Unit) {
        vm.uiAction.collect { action ->
            when (action) {
                ChatUiAction.OpenModelPicker -> showModelPicker = true
                ChatUiAction.OpenSessions -> showSessions = true
                ChatUiAction.OpenSkillHub -> nav.navigate(com.thoragent.app.ui.navigation.ThorRoute.Skills.path)
                ChatUiAction.OpenTools -> nav.navigate(com.thoragent.app.ui.navigation.ThorRoute.Skills.path)
                ChatUiAction.OpenHelp -> showSlashPalette = true
                ChatUiAction.FocusComposer -> composerFocus.requestFocus()
                ChatUiAction.PickImage -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                ChatUiAction.PickProfileImport -> importProfile.launch(arrayOf("application/zip", "application/octet-stream"))
                ChatUiAction.RestartApp -> (context as? android.app.Activity)?.recreate()
                ChatUiAction.QuitApp -> (context as? android.app.Activity)?.moveTaskToBack(true)
                is ChatUiAction.OpenUrl -> runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(action.url)))
                }
                is ChatUiAction.ShareFile -> shareFile(action.path, action.mime)
            }
        }
    }
    LaunchedEffect(toast) { if (toast.isNotBlank()) snackbar.showSnackbar(toast) }

    // indikator baterai (di-refresh berkala saat ditampilkan)
    LaunchedEffect(batteryOn, statusbarOn) {
        if (batteryOn) {
            while (true) {
                batteryPct = vm.batteryStatus()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    // ---- scroll pintar: ikuti bila di dasar, hitung unread bila tidak ----
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount == 0 ||
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.size, streaming.length, waitingFirstToken) {
        val extra = if (streaming.isNotBlank() || waitingFirstToken) 1 else 0
        val total = messages.size + extra
        if (total > 0) {
            if (atBottom) {
                listState.animateScrollToItem(total - 1)
                unread = 0
            } else {
                unread += 1
            }
        }
    }
    // sesi awal: rumah (/sethome) bila ada, kalau tidak sesi terbaru
    LaunchedEffect(Unit) {
        val home = vm.homeSessionId.first()
        if (home > 0) vm.selectSession(home)
    }
    LaunchedEffect(sessionId, sessions) {
        if (sessions.isNotEmpty() && sessionId == null) vm.selectSession(sessions.first().id)
    }

    val modelName = when {
        sessionModel.isNotBlank() -> sessionModel
        effectiveModel.isNotBlank() -> effectiveModel
        else -> providerInfo.substringAfter("· ").ifBlank { "auto" }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hamburger — buka SIDEBAR ala Gemini (chat baru, riwayat)
                        IconButton(onClick = { onOpenDrawer() }) {
                            Icon(
                                com.thoragent.app.ui.icons.ThorIconsExtra.Menu,
                                "Buka menu riwayat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        Icon(ThorIcons.Bolt, null, tint = gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thor Agent", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    // chip model — sentuh untuk ganti (otomatis/manual)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = gold.copy(alpha = 0.12f),
                        modifier = Modifier.padding(end = 4.dp).clickable { showModelPicker = true }
                    ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(ThorIcons.AutoAwesome, null, tint = gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            modelName,
                            style = MaterialTheme.typography.labelMedium,
                            color = gold,
                            maxLines = 1
                        )
                    }
                    }
                    IconButton(onClick = { showSessions = true }) {
                        Icon(ThorIcons.History, "Riwayat sesi")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(ThorIcons.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sesi baru") },
                            leadingIcon = { Icon(ThorIcons.Add, null, tint = gold, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; vm.send("/new") }
                        )
                        DropdownMenuItem(
                            text = { Text("Pilih model") },
                            leadingIcon = { Icon(ThorIcons.AutoAwesome, null, tint = gold, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; showModelPicker = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Ganti gaya jawaban") },
                            leadingIcon = { Icon(ThorIcons.Person, null, tint = gold, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; input = "/personality " }
                        )
                        DropdownMenuItem(
                            text = { Text("Ganti tema tampilan") },
                            leadingIcon = { Icon(ThorIcons.Settings, null, tint = gold, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; input = "/skin " }
                        )
                        DropdownMenuItem(
                            text = { Text("Bantuan perintah") },
                            leadingIcon = { Icon(ThorIcons.Info, null, tint = gold, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; showSlashPalette = true }
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // ---- strip status mini: goal + model + token + antrean + baterai ----
            if (statusbarOn) {
                StatusStrip(
                    model = modelName,
                    tokens = usage.totalTokens,
                    queued = queue.size,
                    bg = bgJobs.size,
                    battery = batteryPct,
                    showBattery = batteryOn,
                    paused = pausedNow
                )
            }
            if (goal.isNotBlank()) {
                Surface(
                    color = amber.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(ThorIcons.Lightbulb, null, tint = amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "goal: ${goal.take(70)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = amber,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            "kelola",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary,
                            modifier = Modifier.clickable { input = "/goal " }
                        )
                    }
                }
            }

            // ---- daftar pesan + FAB gulir-ke-dasar ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val visible = if (focusMode) messages.filter { it.kind == "text" } else messages
                    items(visible, key = { it.id }) { m ->
                        MessageRow(
                            m = m,
                            modifier = Modifier.animateItem(),
                            showTime = timestamps,
                            verbose = verboseMode,
                            gold = gold,
                            onCopy = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Thor", m.content))
                            },
                            onQuote = { input = "\"${m.content.take(160)}\"\n\n" },
                            onEdit = { vm.editFrom(m.id) }
                        )
                    }
                    if (waitingFirstToken && streaming.isBlank()) {
                        item(key = "waiting") { BouncingDots(modelName, gold) }
                    }
                    if (streaming.isNotBlank()) {
                        item(key = "streaming") {
                            Bubble(role = "assistant", text = streaming + " ▍", model = modelName, gold = gold)
                        }
                    }
                    if (messages.isEmpty() && streaming.isBlank() && !waitingFirstToken) {
                        item(key = "empty") {
                            EmptyState(pet = petName, gold = gold, onSuggestion = { sug -> vm.send(sug) })
                        }
                    }
                }

                // FAB gulir ke bawah — muncul saat pengguna membaca ke atas
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && messages.isNotEmpty(),
                    enter = fadeIn() + scaleIn(initialScale = 0.6f, animationSpec = tween(200)),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            unread = 0
                            scope.launch {
                                val extra = if (streaming.isNotBlank() || waitingFirstToken) 1 else 0
                                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + extra - 1)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp, 8.dp)
                    ) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(ThorIcons.ExpandMore, "Gulir ke pesan terbaru")
                        }
                    }
                }
            }

            // ---- chip antrean & lampiran ----
            AnimatedVisibility(visible = queue.isNotEmpty()) {
                Surface(
                    color = gold.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clickable { showQueue = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(ThorIcons.Schedule, null, tint = gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${queue.size} pesan mengantre",
                            style = MaterialTheme.typography.labelSmall,
                            color = gold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "kelola",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                    }
                }
            }
            AnimatedVisibility(visible = attachment != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(ThorIcons.Image, null, tint = gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            attachment?.substringAfterLast("/") ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            "lepas",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorErr,
                            modifier = Modifier.clickable { vm.clearAttachment() }
                        )
                    }
                }
            }

            // ==================== COMPOSER MENGAMBANG ====================
            // Perbaikan "kolom ketik tenggelam": naik mengikuti keyboard
            // (imePadding), warna permukaan lebih tinggi + garis gradasi emas.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.imePadding()) {
                    // garis rambut gradasi emas — pemisah lembut dari area chat
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, gold.copy(alpha = 0.55f), Color.Transparent)
                                )
                            )
                    )

                    // autocomplete perintah langsung saat mengetik "/"
                    val query = input.removePrefix("/").substringBefore(' ').lowercase()
                    if (input.startsWith("/") && query.isNotBlank()) {
                        val matches = remember(query, slashCommands) {
                            slashCommands.filter {
                                it.name.startsWith(query, true) || it.aliases.any { a -> a.startsWith(query, true) }
                            }.take(3)
                        }
                        if (matches.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                matches.forEach { c ->
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = gold.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, gold.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable {
                                            input = "/${c.name} "
                                        }
                                    ) {
                                        Text(
                                            "/${c.name}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = gold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (input.isEmpty() && !busy) {
                        // ---- SLASH COMMAND JADI TOMBOL (v0.5.0) ----
                        // Perintah populer tinggal disentuh — tanpa mengetik "/".
                        QuickActions(
                            onSend = { cmd -> vm.send(cmd) },
                            onPrefill = { text -> input = text },
                            expanded = messages.isEmpty()
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // tombol slash → palet perintah penuh
                        IconButton(onClick = { showSlashPalette = true }) {
                            Icon(ThorIcons.Bolt, "Perintah", tint = gold, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(2.dp))
                        // lampirkan gambar (/image)
                        IconButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(ThorIcons.AttachFile, "Lampirkan gambar", tint = ThorTextSecondary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(2.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp, max = 150.dp)
                                .focusRequester(composerFocus),
                            placeholder = { Text("Tanya Thor…") },
                            maxLines = 6,
                            shape = RoundedCornerShape(24.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = gold,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = ThorSurfaceHigh,
                                unfocusedContainerColor = ThorSurfaceHigh,
                                cursorColor = gold
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = busy,
                            transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f)).togetherWith(
                                    fadeOut(tween(120)) + scaleOut(targetScale = 0.7f)
                                )
                            },
                            label = "sendstop"
                        ) { isBusy ->
                            if (isBusy) {
                                Surface(shape = CircleShape, color = ThorErr.copy(alpha = 0.15f)) {
                                    IconButton(onClick = { vm.stopAgent() }) {
                                        Icon(ThorIcons.Stop, "Hentikan", tint = ThorErr)
                                    }
                                }
                            } else {
                                val interaction = remember { MutableInteractionSource() }
                                val pressed by interaction.collectIsPressedAsState()
                                val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "send")
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .scale(scale)
                                        .background(
                                            Brush.linearGradient(listOf(gold, amber)),
                                            CircleShape
                                        )
                                ) {
                                    IconButton(
                                        interactionSource = interaction,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            vm.send(input)
                                            input = ""
                                        },
                                        enabled = input.isNotBlank() || attachment != null
                                    ) {
                                        Icon(ThorIcons.Send, "Kirim", tint = ThorBg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- palet slash command interaktif ----
    SlashPalette(
        vm = vm,
        visible = showSlashPalette,
        onDismiss = { showSlashPalette = false }
    ) { cmd ->
        showSlashPalette = false
        val hint = cmd.argsHint
        val hasArgs = hint.isNotBlank() && !hint.startsWith("[")
        val hasOptionalArgs = hint.startsWith("[")
        when {
            cmd.name in setOf(
                "title", "goal", "plan", "learn", "queue", "bg", "btw", "steer",
                "model", "heartbeat", "loop", "cron", "subgoal", "skin", "personality",
                "worktree", "diff", "init", "refine", "review", "moa", "kanban",
                "suggestions", "blueprint", "curator", "rollback", "hatch", "fast", "bg"
            ) -> input = "/${cmd.name} "
            hasArgs -> input = "/${cmd.name} "
            hasOptionalArgs || cmd.subcommands.isNotEmpty() -> input = "/${cmd.name} "
            else -> vm.send("/${cmd.name}")
        }
    }

    // ---- pemilih model (hanya daftar hidup dari endpoint sendiri) ----
    ModelPickerSheet(
        vm = vm,
        visible = showModelPicker,
        onDismiss = { showModelPicker = false },
        onPick = { model, global ->
            showModelPicker = false
            if (global) vm.setGlobalModel(model) else vm.setSessionModel(model)
        }
    )

    // ---- lembar sesi ----
    if (showSessions) {
        ModalBottomSheet(onDismissRequest = { showSessions = false }) {
            SessionList(
                sessions = sessions,
                currentId = sessionId,
                onSelect = { vm.selectSession(it); showSessions = false },
                onDelete = { vm.deleteSession(it) },
                onNew = { vm.send("/new"); showSessions = false }
            )
        }
    }

    // ---- lembar antrean (/queue) ----
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(
                queue = queue,
                onRemove = { vm.removeFromQueue(it) },
                onClear = { vm.clearQueue() },
                onDismiss = { showQueue = false }
            )
        }
    }

    // ---- dialog persetujuan perintah berbahaya ----
    approval?.let { ap ->
        AlertDialog(
            onDismissRequest = { vm.respondApproval(false) },
            icon = { Icon(ThorIcons.Warning, null, tint = ThorErr) },
            title = { Text("Izinkan perintah ini?") },
            text = {
                Column {
                    Text("Tool: ${ap.toolName}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Surface(
                        color = Color(0xFF101014),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            ap.command,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE8E8EC),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (ap.reason != null) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Terdeteksi: ${ap.reason}",
                            color = ThorErr,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        "Approval floor ala Thor-agent — pola berbahaya dari tools/approval_detection repo sumber.",
                        color = ThorTextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.respondApproval(true) }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { vm.respondApproval(false) }) { Text("Tolak") } }
        )
    }
}

// ==================== komponen ====================

/** Strip status mini — model, token, antrean, baterai (/statusbar /battery). */
@Composable
private fun StatusStrip(
    model: String,
    tokens: Long,
    queued: Int,
    bg: Int,
    battery: Pair<Int, Boolean>,
    showBattery: Boolean,
    paused: Boolean
) {
    val gold = MaterialTheme.colorScheme.primary
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(ThorIcons.AutoAwesome, null, tint = gold, modifier = Modifier.size(16.dp))
            Text(
                model,
                style = MaterialTheme.typography.labelMedium,
                color = gold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text("·", color = ThorTextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(
                "$tokens tk",
                style = MaterialTheme.typography.labelMedium,
                color = ThorTextSecondary
            )
            if (queued > 0) {
                Text("·", color = ThorTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text("$queued antre", style = MaterialTheme.typography.labelMedium, color = gold)
            }
            if (bg > 0) {
                Text("·", color = ThorTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text("$bg bg", style = MaterialTheme.typography.labelMedium, color = ThorTextSecondary)
            }
            if (showBattery && battery.first >= 0) {
                Text("·", color = ThorTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(
                    buildString {
                        append(battery.first); append("%")
                        if (battery.second) append(" mengisi")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        battery.first <= 15 && !battery.second -> ThorErr
                        battery.second -> ThorOk
                        else -> ThorTextSecondary
                    }
                )
            }
            if (paused) {
                Text("·", color = ThorTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text("DIJEDA", style = MaterialTheme.typography.labelMedium, color = ThorErr, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Indikator "sedang berpikir" — tiga titik emas melompat (hidup, bukan spinner kaku). */
@Composable
private fun BouncingDots(model: String, gold: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(role = "assistant", gold = gold)
        Spacer(Modifier.width(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = ThorSurfaceHigh)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "dots")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..2).forEach { i ->
                        val dy by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = -7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(360, delayMillis = i * 130, easing = EaseOutBack),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "d$i"
                        )
                        Box(
                            Modifier
                                .offset(y = dy.dp)
                                .size(7.dp)
                                .background(gold, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "$model sedang berpikir…",
                    style = MaterialTheme.typography.bodySmall,
                    color = ThorTextSecondary
                )
            }
        }
    }
}

@Composable
private fun Avatar(role: String, gold: Color) {
    val isUser = role == "user"
    val bg = if (isUser) gold else MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
    val fg = if (isUser) ThorBg else gold
    Box(
        Modifier.size(30.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isUser) ThorIcons.Person else ThorIcons.SmartToy,
            null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Layar kosong: logo berdenyut + pet maskot + chip saran — terasa hidup. */
@Composable
private fun EmptyState(pet: String, gold: Color, onSuggestion: (String) -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow"
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bounce"
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // logo petir Thor berdenyut
        Box(
            Modifier
                .size(84.dp)
                .background(
                    Brush.linearGradient(
                        listOf(gold.copy(alpha = 0.22f * glow + 0.08f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(ThorIcons.Bolt, null, tint = gold.copy(alpha = glow), modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text("Thor Agent Mobile", style = MaterialTheme.typography.titleLarge)
        Text(
            "Asisten pribadi yang bisa membaca berkas, menjalankan terminal, " +
                "dan memakai 44 skill dari repo Thor-agent.",
            color = ThorTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        // pet maskot (/pet /hatch)
        if (pet.isNotBlank()) {
            val seg = pet.split("#")
            val size = when (seg.getOrNull(2)) { "s" -> 26.dp; "l" -> 44.dp; else -> 34.dp }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.offset(y = bounce.dp)
            ) {
                Icon(ThorIcons.Pets, null, tint = gold, modifier = Modifier.size(size))
                Column {
                    Text(seg.getOrNull(0)?.ifBlank { "pet" } ?: "pet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("si ${seg.getOrNull(1) ?: "petdex"}", style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
                }
            }
            Spacer(Modifier.size(6.dp))
        }
        Spacer(Modifier.size(8.dp))
        // 4 saran inti, label saja — cukup mengarahkan tanpa memenuhi layar
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip("/status", null, gold, onSuggestion)
            SuggestionChip("Jelajahi berkas", null, gold, onSuggestion)
            SuggestionChip("/skills", null, gold, onSuggestion)
            SuggestionChip("/help", null, gold, onSuggestion)
        }
    }
}

@Composable
private fun SuggestionChip(label: String, sub: String?, gold: Color, onSuggestion: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "chip")
    Surface(
        shape = RoundedCornerShape(50),
        color = gold.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, gold.copy(alpha = 0.35f)),
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null) { onSuggestion(label) }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = gold, fontWeight = FontWeight.Medium)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
            }
        }
    }
}

/** Satu baris pesan — animasi masuk + menu tekan-lama (salin/kutip/edit). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: MessageEntity,
    modifier: Modifier = Modifier,
    showTime: Boolean,
    verbose: String,
    gold: Color,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onEdit: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(modifier) {
        when {
            m.role == "user" && m.kind == "text" -> Bubble("user", m.content, null, gold, showTime, m.createdAt) { menu = true }
            m.role == "assistant" && m.kind == "text" -> Bubble("assistant", m.content, null, gold, showTime, m.createdAt) { menu = true }
            m.kind == "tool_call" && verbose != "off" -> ToolCallCard(toolName = m.toolName ?: "?", args = m.toolArgs ?: "{}", gold = gold, expandedDefault = verbose == "all")
            m.kind == "tool_result" && verbose != "off" -> ToolResultCard(toolName = m.toolName, result = m.content, gold = gold, expandedDefault = verbose == "all")
            m.kind == "error" || m.role == "error" -> Bubble("error", m.content, null, gold, showTime, m.createdAt) { menu = true }
            else -> if (m.kind == "text") Bubble("assistant", m.content, null, gold, showTime, m.createdAt) { menu = true }
        }
        if (menu && m.kind == "text") {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Salin") },
                    leadingIcon = { Icon(ThorIcons.ContentCopy, null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text("Kutip") },
                    leadingIcon = { Icon(ThorIcons.Chat, null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onQuote() }
                )
                if (m.role == "user") {
                    DropdownMenuItem(
                        text = { Text("Edit & kirim ulang") },
                        leadingIcon = { Icon(ThorIcons.Edit, null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; onEdit() }
                    )
                }
            }
        }
    }
    // haptic dipicu saat menu dibuka (long-press)
    if (menu) LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bubble(
    role: String,
    text: String,
    model: String? = null,
    gold: Color,
    showTime: Boolean = false,
    createdAt: Long = 0L,
    onLongClick: () -> Unit = {}
) {
    val isUser = role == "user"
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isUser) Avatar(role, gold)
            Column(Modifier.widthIn(max = 320.dp)) {
                if (!isUser && model != null) {
                    Text(
                        model,
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                    )
                }
                val bg = when (role) {
                    "user" -> gold.copy(alpha = 0.16f)
                    "error" -> ThorErr.copy(alpha = 0.18f)
                    else -> ThorSurfaceHigh
                }
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = bg,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    )
                ) {
                    if (role == "error") {
                        Text(
                            text,
                            color = ThorErr,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else if (isUser) {
                        Text(
                            text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        MarkdownText(text, modifier = Modifier.padding(12.dp))
                    }
                }
                if (showTime && createdAt > 0) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale("id")).format(Date(createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }
            if (isUser) Avatar(role, gold)
        }
    }
}

/** Ikon per tool — meniru marker tool TUI repo sumber. */
@Composable
private fun toolIcon(name: String) = when {
    name == "shell" -> ThorIcons.Terminal
    name.startsWith("read_") || name.startsWith("write_") || name.startsWith("edit_") -> ThorIcons.Description
    name == "list_dir" -> ThorIcons.FolderOpen
    name == "search_files" -> ThorIcons.Search
    name == "github_clone" -> ThorIcons.Code
    name == "web_fetch" -> ThorIcons.Link
    name.startsWith("skill_") -> ThorIcons.Memory
    name == "root_status" -> ThorIcons.Security
    else -> ThorIcons.Bolt
}

/** Kartu panggilan tool — lipat, animasi mulus (accordion ala TUI Thor-agent). */
@Composable
fun ToolCallCard(toolName: String, args: String, gold: Color, expandedDefault: Boolean = false) {
    var expanded by remember { mutableStateOf(expandedDefault) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(toolIcon(toolName), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    toolName,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "  menjalankan…",
                    style = MaterialTheme.typography.labelSmall,
                    color = ThorTextSecondary
                )
                Spacer(Modifier.weight(1f))
                val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
                Icon(
                    ThorIcons.ExpandMore,
                    if (expanded) "Tutup" else "Buka",
                    tint = ThorTextSecondary,
                    modifier = Modifier.size(18.dp).scale(1f).rotate(rot)
                )
            }
            AnimatedVisibility(
                expanded,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 }
            ) {
                Column {
                    Spacer(Modifier.size(6.dp))
                    Surface(color = Color(0xFF101014), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            prettyArgs(args).take(2000),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE8E8EC),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun prettyArgs(args: String): String = runCatching {
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(args)
        as? kotlinx.serialization.json.JsonObject ?: return args
    obj.entries.joinToString("\n") { (k, v) ->
        val s = v.toString().removeSurrounding("\"")
        "$k: ${s.take(160)}"
    }
}.getOrDefault(args)

/** Kartu hasil tool — lipat, status ok/gagal jelas. */
@Composable
fun ToolResultCard(toolName: String?, result: String, gold: Color, expandedDefault: Boolean = false) {
    var expanded by remember { mutableStateOf(expandedDefault) }
    val ok = result.contains("\"ok\":true") || result.contains("\"ok\": true")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = (if (ok) ThorOk else ThorErr).copy(alpha = 0.09f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) ThorIcons.CheckCircle else ThorIcons.Cancel,
                    if (ok) "Berhasil" else "Gagal",
                    tint = if (ok) ThorOk else ThorErr,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append("hasil")
                        if (toolName != null) append(" $toolName")
                        append(" · ${result.length} kar")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) ThorOk else ThorErr
                )
                Spacer(Modifier.weight(1f))
                val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev2")
                Icon(
                    ThorIcons.ExpandMore,
                    if (expanded) "Tutup" else "Buka",
                    tint = ThorTextSecondary,
                    modifier = Modifier.size(18.dp).rotate(rot)
                )
            }
            AnimatedVisibility(
                expanded,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 }
            ) {
                Surface(color = Color(0xFF101014), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        result.take(3000),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8E8EC),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/** Lembar antrean pesan (/queue) — kelola pesan yang menunggu agent. */
@Composable
private fun QueueSheet(
    queue: List<String>,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val gold = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ThorIcons.Schedule, null, tint = gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Antrean Pesan", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (queue.isNotEmpty()) {
                TextButton(onClick = { onClear() }) { Text("Kosongkan", color = ThorErr) }
            }
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
        if (queue.isEmpty()) {
            Text(
                "Tidak ada pesan mengantre. Pesan yang dikirim saat agent bekerja otomatis muncul di sini.",
                color = ThorTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(queue.size) { i ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, color = gold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                queue[i],
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            IconButton(onClick = { onRemove(i) }) {
                                Icon(ThorIcons.Close, "Hapus dari antrean", tint = ThorErr, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<com.thoragent.app.data.db.SessionEntity>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit
) {
    val gold = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ThorIcons.History, null, tint = gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Riwayat Sesi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNew) {
                Icon(ThorIcons.Add, null, modifier = Modifier.size(16.dp))
                Text("Baru")
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions.take(30), key = { it.id }) { s ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (s.id == currentId) gold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clickable { onSelect(s.id) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (s.id == currentId) FontWeight.Bold else FontWeight.Normal,
                                color = if (s.id == currentId) gold else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                SimpleDateFormat("dd MMM HH:mm", Locale("id")).format(Date(s.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = ThorTextSecondary
                            )
                        }
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(ThorIcons.Delete, "Hapus", tint = ThorErr, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (sessions.isEmpty()) {
            Text("Belum ada sesi.", color = ThorTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
