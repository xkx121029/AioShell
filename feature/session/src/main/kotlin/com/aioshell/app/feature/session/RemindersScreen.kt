package com.aioshell.app.feature.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.reminder.Reminder
import com.aioshell.app.core.data.reminder.ReminderManager
import com.aioshell.app.core.ui.components.LoadingState
import com.aioshell.app.core.ui.theme.AppSpacing
import com.aioshell.app.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderManager: ReminderManager,
) : ViewModel() {
    private val _reminders = MutableStateFlow<List<Reminder>?>(null)
    val reminders: StateFlow<List<Reminder>?> = _reminders.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        reminderManager.removeExpired()
        _reminders.value = reminderManager.list()
    }

    fun add(title: String, content: String, triggerAtMs: Long) = viewModelScope.launch {
        reminderManager.add(title, content, triggerAtMs)
        refresh()
    }

    fun remove(id: String) = viewModelScope.launch {
        reminderManager.remove(id)
        refresh()
    }
}

/** 定时提醒页：查看、新增（日期+时间）、删除提醒。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 用户选择，无论同意与否都继续添加 */ }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("定时提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // 安卓 13+ 首次使用先请求通知权限，再打开新增弹窗
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        showAdd = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增提醒", tint = c.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { inner ->
        val list = reminders
        if (list == null) {
            LoadingState(modifier = Modifier.padding(inner))
        } else if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                Text("暂无提醒", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("点击右上角 + 新建", color = c.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                items(list, key = { it.id }) { r ->
                    ReminderRow(r, onDelete = { viewModel.remove(r.id) })
                }
            }
        }
    }

    if (showAdd) {
        AddReminderDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, content, ts ->
                viewModel.add(title, content, ts)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ReminderRow(r: Reminder, onDelete: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surfaceVariant, MaterialTheme.shapes.medium).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.onSurface)
            if (r.content.isNotBlank()) {
                Text(r.content, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Text(
                formatTime(r.triggerAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = c.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, triggerAtMs: Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }
    val c = AppTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建提醒") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("标题") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = content, onValueChange = { content = it }, label = { Text("内容（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AppSpacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDate = true }) {
                        Text(formatDate(calendar.timeInMillis), color = c.primary)
                    }
                    TextButton(onClick = { showTime = true }) {
                        Text(formatTime(calendar.timeInMillis), color = c.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onConfirm(title.trim(), content.trim(), calendar.timeInMillis)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = calendar.timeInMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { calendar.timeInMillis = it }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    with(calendar) {
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                    }
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
        )
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))