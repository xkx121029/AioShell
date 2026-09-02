package com.aioshell.app.feature.persona

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.model.Persona

/** 人格预设管理页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(onBack: () -> Unit, viewModel: PersonaViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Persona?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("人格预设", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::addPersona) {
                Icon(Icons.Filled.Add, contentDescription = "新增人格")
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("为对话注入固定的身份与回答风格", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(
                        onClick = { viewModel.select("") },
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.currentId.isEmpty()) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认（不注入人格）", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            items(ui.personas, key = { it.id }) { persona ->
                PersonaCard(
                    persona = persona,
                    selected = persona.id == ui.currentId,
                    onSelect = { viewModel.select(persona.id) },
                    onEdit = { editing = persona },
                    onDelete = { viewModel.delete(persona.id) },
                )
            }
        }
    }

    editing?.let { p ->
        PersonaEditDialog(
            persona = p,
            isBuiltin = p.builtin,
            onDismiss = { editing = null },
            onConfirm = { name, identity, style, prompt ->
                viewModel.update(p.id, name, identity, style, prompt)
                editing = null
            },
        )
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) c.primaryContainer else c.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    persona.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }
                if (!persona.builtin) {
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除") }
                }
            }
            if (persona.identity.isNotBlank()) {
                Text(persona.identity, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            if (persona.style.isNotBlank()) {
                Text(persona.style, style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant.copy(alpha = 0.85f))
            }
            if (selected) {
                Row(Modifier.padding(top = 6.dp)) {
                    Surface(color = c.primary, shape = MaterialTheme.shapes.extraSmall) {
                        Text("当前",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = c.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaEditDialog(
    persona: Persona,
    isBuiltin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, identity: String, style: String, prompt: String) -> Unit,
) {
    var name by remember { mutableStateOf(persona.name) }
    var identity by remember { mutableStateOf(persona.identity) }
    var style by remember { mutableStateOf(persona.style) }
    var prompt by remember { mutableStateOf(persona.prompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBuiltin) "编辑人格（内置仅可改名）" else "编辑人格") },
        text = {
            Column(Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!isBuiltin) {
                    OutlinedTextField(identity, { identity = it }, label = { Text("身份定位") },
                        placeholder = { Text("如：资深软件工程师") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(style, { style = it }, label = { Text("回答风格") },
                        placeholder = { Text("如：简洁、先给结论") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("行为约束（可选）") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name, identity, style, prompt)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}