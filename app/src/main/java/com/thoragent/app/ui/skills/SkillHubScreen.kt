package com.thoragent.app.ui.skills

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
            title = { Text("SKILL.md", style = MaterialTheme.typography.titleMedium) },
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
                "Prosedur langkah-demi-langkah yang bisa dipakai agent.",
                style = MaterialTheme.typography.labelMedium,
                color = ThorTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Skill") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tool") })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cari skill atau tool…") },
                leadingIcon = { Icon(ThorIcons.Search, null, tint = ThorGold, modifier = Modifier.size(20.dp)) },
                singleLine = true
            )
            if (tab == 0) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c) }
                        )
                    }
                }
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredSkills, key = { it.origin + it.path }) { s ->
                        Card(
                            onClick = { vm.openSkill(s.path) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Icon(
                                        if (s.origin == "user") ThorIcons.AutoAwesome else ThorIcons.MenuBook,
                                        null,
                                        tint = if (s.origin == "user") ThorOk else ThorAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        s.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ThorAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        when (s.origin) {
                                            "builtin" -> "bawaan"
                                            "user" -> "dipelajari"
                                            else -> "opsional"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ThorTextSecondary
                                    )
                                }
                                if (s.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        s.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThorTextSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTools, key = { it.name }) { t ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    t.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ThorAmber
                                )
                                if (t.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        t.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThorTextSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
