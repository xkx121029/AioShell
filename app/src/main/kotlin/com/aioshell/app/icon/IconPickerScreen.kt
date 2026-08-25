package com.aioshell.app.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class IconPickerViewModel @Inject constructor(private val switcher: IconSwitcher) : ViewModel() {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    init {
        viewModelScope.launch {
            switcher.current.collect { _current.value = it }
        }
    }

    val options = switcher.options
    fun nameOf(name: String) = switcher.label(name)

    fun pick(name: String) = viewModelScope.launch { switcher.pick(name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    onBack: () -> Unit,
    viewModel: IconPickerViewModel = hiltViewModel(),
) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择应用图标", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(viewModel.options) { name ->
                IconTile(
                    name = name,
                    label = viewModel.nameOf(name),
                    isSelected = name == current,
                    onClick = { viewModel.pick(name) },
                )
            }
        }
    }
}

@Composable
private fun IconTile(
    name: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val resId = when (name) {
        "a" -> com.aioshell.app.R.drawable.ic_icon_opt_a
        "b" -> com.aioshell.app.R.drawable.ic_icon_opt_b
        else -> com.aioshell.app.R.drawable.ic_launcher_foreground
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) c.primary else c.outlineVariant)
        ) {
            Image(
                painter = painterResource(resId),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "已选",
                    tint = c.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) c.primary else c.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}