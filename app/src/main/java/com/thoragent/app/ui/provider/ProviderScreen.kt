package com.thoragent.app.ui.provider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.core.provider.ModelCatalog
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorAmber
import com.thoragent.app.ui.theme.ThorErr
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderScreen(nav: NavHostController) {
    val vm: ProviderViewModel = viewModel()
    val profiles by vm.profiles.collectAsState()
    val edit by vm.edit.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val busy by vm.busy.collectAsState()
    val liveModels by vm.liveModels.collectAsState()
    var showModelPicker by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan Provider") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(ThorIcons.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newProfile() }) {
                        Icon(ThorIcons.Add, "Provider baru", tint = ThorGold)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Semua provider OpenAI-compatible: OpenRouter, OpenAI, Anthropic-compatible, " +
                        "atau server lokal Anda (mis. LM Studio di http://192.168.1.10:1234/v1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ThorTextSecondary
                )
            }
            items(profiles, key = { it.id }) { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (p.isDefault)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ThorIcons.Key, null, tint = ThorGold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                if (p.isDefault) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = ThorOk.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "bawaan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ThorOk,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "${p.baseUrl} · ${if (p.model.isBlank()) "model belum dipilih!" else p.model}",
                                style = MaterialTheme.typography.bodySmall, color = ThorTextSecondary
                            )
                            Text(
                                "temp=${p.temperature} · max=${p.maxTokens} · ${if (p.streaming) "streaming" else "non-stream"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThorTextSecondary
                            )
                        }
                        if (!p.isDefault) {
                            IconButton(onClick = { vm.setDefault(p.id) }) {
                                Icon(ThorIcons.CheckCircle, "Jadikan bawaan", tint = ThorOk)
                            }
                        }
                        IconButton(onClick = { vm.editProfile(p) }) {
                            Icon(ThorIcons.Edit, "Ubah")
                        }
                        IconButton(onClick = { vm.delete(p.id) }) {
                            Icon(ThorIcons.Delete, "Hapus", tint = ThorErr)
                        }
                    }
                }
            }
            item {
                Text("Formulir Provider", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ---- preset cepat (meniru model_setup_flows repo sumber) ----
                        Text("Preset cepat", style = MaterialTheme.typography.labelMedium, color = ThorTextSecondary)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModelCatalog.PRESETS.forEach { preset ->
                                AssistChip(
                                    onClick = { vm.applyPreset(preset) },
                                    label = { Text(preset.label) },
                                    leadingIcon = { Icon(ThorIcons.Cloud, null, Modifier.size(20.dp)) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = edit.name,
                            onValueChange = { v -> vm.updateEdit { it.copy(name = v) } },
                            label = { Text("Nama profil") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edit.baseUrl,
                            onValueChange = { v -> vm.updateEdit { it.copy(baseUrl = v) } },
                            label = { Text("Base URL (…/v1)") },
                            placeholder = { Text("https://openrouter.ai/api/v1") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edit.apiKey,
                            onValueChange = { v -> vm.updateEdit { it.copy(apiKey = v) } },
                            label = { Text(if (edit.editingId == null) "API Key" else "API Key (kosong = tetap)") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text("Disimpan terenkripsi Android Keystore (AES/GCM).")
                            }
                        )

                        // ---- model: wajib eksplisit (dari daftar hidup provider) ----
                        OutlinedTextField(
                            value = edit.model,
                            onValueChange = { v -> vm.updateEdit { it.copy(model = v) } },
                            label = { Text("Model (wajib — dari provider Anda)") },
                            placeholder = { Text("pilih dari daftar provider…") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    vm.refreshLiveModels()
                                    showModelPicker = true
                                }) {
                                    Icon(ThorIcons.AutoAwesome, "Pilih model", tint = ThorGold)
                                }
                            },
                            supportingText = {
                                Text(
                                    if (edit.model.isBlank())
                                        "Belum dipilih — tekan ikon bintang untuk memuat daftar model " +
                                            "langsung dari base URL Anda (tidak ada default otomatis)."
                                    else "Model eksplisit \"${edit.model}\" — selalu dipakai apa adanya."
                                )
                            }
                        )

                        Text("Temperature: ${"%.2f".format(edit.temperature)}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Kreativitas jawaban: 0–0.3 = tepat & konsisten (tugas teknis, " +
                                "edit kode) · 0.7–1.0 = seimbang (default) · >1.0 = sangat " +
                                "kreatif tapi sering melenceng. Tidak mengubah biaya token.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                        Slider(
                            value = edit.temperature.toFloat(),
                            onValueChange = { v -> vm.updateEdit { it.copy(temperature = v.toDouble()) } },
                            valueRange = 0f..2f,
                            steps = 20
                        )
                        Text("Max tokens: ${edit.maxTokens}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Batas panjang balasan per turn — ini PENJAGA DOMPET: " +
                                "angka kecil = balasan pendek = token terpotong sedikit; " +
                                "agent berhenti otomatis sebelum membengkak. 4096 aman untuk " +
                                "chat biasa; 8192+ untuk tugas panjang (kode/multi-file).",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                        Slider(
                            value = edit.maxTokens.toFloat(),
                            onValueChange = { v -> vm.updateEdit { it.copy(maxTokens = v.toInt()) } },
                            valueRange = 256f..32768f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Streaming (SSE)", Modifier.weight(1f))
                            Switch(
                                checked = edit.streaming,
                                onCheckedChange = { b -> vm.updateEdit { it.copy(streaming = b) } }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Jadikan profil bawaan", Modifier.weight(1f))
                            Switch(
                                checked = edit.setDefault,
                                onCheckedChange = { b -> vm.updateEdit { it.copy(setDefault = b) } }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.testConnection() },
                                enabled = !busy
                            ) { Text(if (busy) "Menguji…" else "Uji Koneksi") }
                            Button(
                                onClick = { vm.save { nav.popBackStack() } },
                                enabled = edit.name.isNotBlank() && edit.baseUrl.isNotBlank() && edit.model.isNotBlank()
                            ) { Text("Simpan") }
                        }
                        testResult?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (it.startsWith("OK")) ThorIcons.CheckCircle else ThorIcons.ErrorOutline,
                                    null,
                                    tint = if (it.startsWith("OK")) ThorOk else ThorErr,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(it, color = if (it.startsWith("OK")) ThorOk else ThorErr, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- pemilih model dalam formulir ----
    if (showModelPicker) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showModelPicker = false }) {
            ModelPickerForm(
                live = liveModels,
                onPick = {
                    vm.updateEdit { e -> e.copy(model = it) }
                    showModelPicker = false
                }
            )
        }
    }
}

@Composable
private fun ModelPickerForm(
    live: List<String>,
    onPick: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
        Text("Pilih Model", style = MaterialTheme.typography.titleMedium)
        Text(
            "Daftar diambil langsung dari base URL profil ini (GET /models) — " +
                "semua model di bawah pasti terdaftar di endpoint, jadi tidak mungkin " +
                "salah model (salah model = penalti saldo terpotong). Tidak ada 'auto'/default.",
            style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary
        )
        if (live.isEmpty()) {
            Text(
                "Daftar model belum termuat. Pastikan base URL & API key sudah benar " +
                    "lalu tekan ikon bintang lagi untuk memuat ulang, atau ketik nama model " +
                    "persis seperti di dokumentasi provider Anda.",
                style = MaterialTheme.typography.labelSmall,
                color = ThorAmber,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (live.isNotEmpty()) {
                item { SectionLabel("Dari provider ini (${live.size})") }
                items(live.take(200), key = { "l" + it }) { id ->
                    PickRow(id, "", false, onPick)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ThorGold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun PickRow(id: String, desc: String, highlight: Boolean, onPick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) ThorGold.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable { onPick(id) }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                id,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight) ThorGold else MaterialTheme.colorScheme.onSurface
            )
            if (desc.isNotBlank()) {
                Text(desc, style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary, maxLines = 2)
            }
        }
    }
}
