package com.thoragent.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorBg
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(nav: NavHostController) {
    val vm: TerminalViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val rootStatus by vm.rootStatus.collectAsState()
    val cwdSetting by vm.cwdSetting.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.sessionId == null, rootStatus?.appGrantedRoot) {
        if (ui.sessionId == null) {
            // mulai sesi otomatis: root bila tersedia
            vm.startSession(root = rootStatus?.appGrantedRoot == true, cwd = cwdSetting)
        }
    }
    LaunchedEffect(ui.lines.size) {
        if (ui.lines.isNotEmpty()) listState.animateScrollToItem(ui.lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(ThorIcons.Terminal, null, tint = ThorGold, modifier = Modifier.padding(end = 6.dp).height(20.dp))
                        Text("Terminal")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.startSession(root = !(ui.rootMode), cwd = ui.cwd)
                    }) {
                        Icon(ThorIcons.Refresh, "Sesi baru / ganti mode", tint = ThorGold)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !ui.rootMode,
                    onClick = { vm.startSession(root = false, cwd = ui.cwd) },
                    label = { Text("sh") }
                )
                FilterChip(
                    selected = ui.rootMode,
                    onClick = { vm.startSession(root = true, cwd = ui.cwd) },
                    label = { Text(if (rootStatus?.appGrantedRoot == true) "su (root)" else "su (perlu grant)") }
                )
                Text(
                    ui.cwd,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(ThorBg)
            ) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                    ) {
                        items(ui.lines) { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = when {
                                    line.startsWith("[E]") -> MaterialTheme.colorScheme.error
                                    line.startsWith("[exit 0]") -> ThorOk
                                    line.startsWith("[exit") -> ThorWarn
                                    line.startsWith("$ ") || line.startsWith("# ") -> ThorGold
                                    line.startsWith("[W]") || line.startsWith("[timeout]") -> ThorWarn
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text(if (ui.rootMode) "# perintah root" else "$ perintah") },
                leadingIcon = { Text(if (ui.rootMode) "#" else "$", color = ThorGold) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            vm.runCommand(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && !ui.busy
                    ) { Icon(ThorIcons.Send, "Jalankan", tint = ThorGold) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            )
        }
    }
}
