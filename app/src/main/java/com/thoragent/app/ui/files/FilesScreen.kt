package com.thoragent.app.ui.files

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.thoragent.app.ui.icons.ThorIcons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.ui.theme.ThorErr
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(nav: NavHostController) {
    val vm: FilesViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { vm.attachSafTree(it) }
    }

    // Refresh status izin tiap kali layar AKTIF kembali (bukan sekali di
    // LaunchedEffect pertama): user memberi izin di layar Pengaturan lalu
    // kembali — tanpa ini status "belum aktif" menempel padahal izin sudah ada.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Berkas — ${ui.dirTitle}") },
                actions = {
                    IconButton(onClick = { vm.up() }) { Icon(ThorIcons.ArrowUpward, "Naik", tint = ThorGold) }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Akses berkas gaya Termux", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (ui.allFilesGranted) "All-Files-Access: AKTIF ✓" else "All-Files-Access: belum aktif",
                            color = if (ui.allFilesGranted) MaterialTheme.colorScheme.primary else ThorErr
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.requestAllFilesIntent()?.let {
                                        context.startActivity(it)
                                    } ?: run {
                                        // API < 30: langsung jelajah /sdcard
                                        vm.browseDir("/sdcard")
                                    }
                                }
                            ) { Text("Minta Akses Semua Berkas") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { safLauncher.launch(null) }) {
                                Icon(ThorIcons.FolderOpen, null, tint = ThorGold)
                                Text("Pilih folder (SAF)")
                            }
                        }
                        Text(
                            "CWD aktif (untuk chat & terminal): ${ui.cwd}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                        OutlinedButton(onClick = { vm.setCurrentDirAsCwd() }) {
                            Text("Jadikan direktori ini CWD")
                        }
                    }
                }
            }
            ui.error?.let { err ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ThorErr.copy(alpha = 0.15f))) {
                        Text(err, Modifier.padding(8.dp), color = ThorErr, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ui.preview?.let { (name, content) ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row {
                                Icon(ThorIcons.Description, null, tint = ThorGold)
                                Spacer(Modifier.padding(4.dp))
                                Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.closePreview() }) {
                                    Icon(ThorIcons.Close, "Tutup pratinjau")
                                }
                            }
                            Text(
                                content,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            items(ui.entries, key = { it.path + it.name }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .clickable { vm.open(row) }
                            .padding(10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            if (row.isDir) ThorIcons.Folder else ThorIcons.Description,
                            null,
                            tint = if (row.isDir) ThorGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontWeight = if (row.isDir) FontWeight.SemiBold else FontWeight.Normal)
                            if (!row.isDir) {
                                Text("${row.size / 1024} KB · ${row.path}", style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
