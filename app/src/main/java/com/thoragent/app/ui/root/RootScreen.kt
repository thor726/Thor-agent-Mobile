package com.thoragent.app.ui.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.core.root.RootStatus
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorAmber
import com.thoragent.app.ui.theme.ThorErr
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorTextSecondary
import com.thoragent.app.ui.theme.ThorWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(nav: NavHostController) {
    val vm: RootViewModel = viewModel()
    val status by vm.status.collectAsState()
    val suTest by vm.suTest.collectAsState()
    val busy by vm.busy.collectAsState()
    val useRoot by vm.useRoot.collectAsState()

    // PERBAIKAN: root sering baru di-grant dari aplikasi manager
    // (KernelSU/APatch/Magisk) — saat kembali ke app, status harus
    // di-probe ulang, bukan menampilkan cache "Tanpa root".
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(ThorIcons.Refresh, "Segarkan", tint = ThorGold) }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { StatusCard(status, busy) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gunakan root saat dibutuhkan", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "Semua eksekusi berjalan shell biasa. Root (su) hanya dipakai " +
                                    "otomatis ketika akses filesystem ditolak — tidak untuk hal lain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ThorTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = useRoot, onCheckedChange = { vm.setUseRoot(it) })
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Uji shell", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.testSh() }, enabled = !busy) { Text("shell biasa") }
                            Button(onClick = { vm.testSu() }, enabled = !busy) { Text("uji su") }
                        }
                        suTest?.let {
                            Text(
                                it,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tanpa root tetap jalan", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Agent, terminal, berkas di /sdcard, unduh repo/web via terminal, dan semua skill " +
                                "berfungsi penuh tanpa root. Root hanya memperluas akses filesystem bila ada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThorTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: RootStatus?, busy: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    if (status?.appGrantedRoot == true) ThorIcons.Security else ThorIcons.Security,
                    null,
                    tint = if (status?.appGrantedRoot == true) ThorOk else ThorTextSecondary,
                    modifier = Modifier.height(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Status Root", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp).padding(2.dp), strokeWidth = 2.dp)
            }
            val st = status
            if (st == null) {
                Text("Memeriksa…", color = ThorTextSecondary)
            } else {
                InfoRow("Akses su (libsu)", if (st.appGrantedRoot) "DIBERIKAN" else "TIDAK", if (st.appGrantedRoot) ThorOk else ThorErr)
                InfoRow("Shell root hidup", if (st.rootShellAlive) "ya" else "tidak", if (st.rootShellAlive) ThorOk else ThorWarn)
                InfoRow("Manager", st.manager.label, when (st.manager) {
                    com.thoragent.app.core.root.RootManagerType.KERNELSU,
                    com.thoragent.app.core.root.RootManagerType.APATCH,
                    com.thoragent.app.core.root.RootManagerType.MAGISK -> ThorAmber
                    else -> ThorTextSecondary
                })
                if (st.managerHint.isNotBlank()) {
                    Text(st.managerHint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Deteksi marker: /data/adb/ksud (KernelSU) · /data/adb/ap|apd (APatch) · /data/adb/magisk (Magisk).",
                    style = MaterialTheme.typography.labelSmall,
                    color = ThorTextSecondary
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
