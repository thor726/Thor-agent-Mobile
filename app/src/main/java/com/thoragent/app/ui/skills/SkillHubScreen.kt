package com.thoragent.app.ui.skills

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.thoragent.app.ui.icons.ThorIcons
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.data.catalog.SkillEntry
import com.thoragent.app.data.catalog.ToolEntry
import com.thoragent.app.ui.theme.ThorAmber
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkillHubViewModel(app: Application) : AndroidViewModel(app) {

    private val rt = com.thoragent.app.runtime.ThorRuntime.runtime(app)

    private val _skills = MutableStateFlow<List<SkillEntry>>(emptyList())
    val skills = _skills.asStateFlow()

    private val _tools = MutableStateFlow<List<ToolEntry>>(emptyList())
    val tools = _tools.asStateFlow()

    private val _doc = MutableStateFlow<String?>(null)
    val doc: StateFlow<String?> = _doc.asStateFlow()

    init {
        viewModelScope.launch {
            _skills.value = rt.catalogs.skills().skills
            _tools.value = rt.catalogs.tools().tools
        }
    }

    fun openSkill(path: String) {
        viewModelScope.launch {
            _doc.value = rt.catalogs.skillDoc(path).ifBlank { "SKILL.md tidak tersedia." }
        }
    }

    fun closeDoc() {
        _doc.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillHubScreen(nav: NavHostController) {
    val vm: SkillHubViewModel = viewModel()
    val skills by vm.skills.collectAsState()
    val tools by vm.tools.collectAsState()
    val doc by vm.doc.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    val rt = com.thoragent.app.runtime.ThorRuntime.runtime(androidx.compose.ui.platform.LocalContext.current)
    val filteredSkills = remember(skills, query, category) {
        var list = rt.catalogs.searchSkills(skills, query)
        if (category != null) list = list.filter { it.category == category }
        list
    }
    val filteredTools = remember(tools, query) { rt.catalogs.searchTools(tools, query) }
    val categories = remember(skills) { skills.groupBy { it.category }.keys.sorted() }

    doc?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.closeDoc() },
            title = { Text("SKILL.md", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        d.take(12000),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.closeDoc() }) { Text("Tutup") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Skill (${skills.size})" else "Tool Inti (${tools.size})") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(ThorIcons.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Kecerdasan dari repo Thor-agent — prosedur langkah-demi-langkah yang dibaca agent.",
                style = MaterialTheme.typography.labelSmall,
                color = ThorTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Skill") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tool") })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Cari nama/deskripsi…") },
                leadingIcon = { Icon(ThorIcons.Search, null, tint = ThorGold) }
            )
            if (tab == 0) {
                Row(
                    Modifier.padding(8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text("$c (${skills.count { it.category == c }})") }
                        )
                    }
                }
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredSkills, key = { it.origin + it.path }) { s ->
                        Card(
                            onClick = { vm.openSkill(s.path) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Icon(
                                        if (s.origin == "user") ThorIcons.AutoAwesome else ThorIcons.MenuBook,
                                        null,
                                        tint = if (s.origin == "user") ThorOk else ThorAmber,
                                        modifier = Modifier.padding(end = 6.dp).height(16.dp)
                                    )
                                    Text(s.name, fontWeight = FontWeight.Bold, color = ThorAmber, modifier = Modifier.weight(1f))
                                    Text(
                                        when (s.origin) {
                                            "builtin" -> "bawaan"
                                            "user" -> "dipelajari"
                                            else -> "opsional"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ThorTextSecondary
                                    )
                                }
                                if (s.description.isNotBlank()) {
                                    Text(s.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "kategori: ${s.category} — sentuh untuk prosedur lengkap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ThorTextSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredTools, key = { it.name }) { t ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                Text(t.name, fontWeight = FontWeight.Bold, color = ThorAmber, style = MaterialTheme.typography.bodyMedium)
                                if (t.description.isNotBlank()) {
                                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = ThorTextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
