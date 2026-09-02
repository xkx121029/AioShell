package com.aioshell.app.feature.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.Persona
import com.aioshell.app.core.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonaUiState(
    val personas: List<Persona> = emptyList(),
    val currentId: String = "",
)

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val repo: PersonaRepository,
) : ViewModel() {

    val uiState: StateFlow<PersonaUiState> =
        combine(repo.personas, repo.currentPersona) { list, current ->
            PersonaUiState(list, current?.id.orEmpty())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PersonaUiState())

    fun select(id: String) = viewModelScope.launch {
        val current = runCatching { repo.currentPersona.first()?.id }.getOrNull().orEmpty()
        repo.setCurrent(if (id == current) "" else id)
    }

    fun addPersona() = viewModelScope.launch {
        repo.upsert(
            Persona(
                id = "custom_${System.currentTimeMillis()}",
                name = "新人格",
                identity = "", style = "", prompt = "",
                builtin = false,
            )
        )
    }

    fun update(id: String, name: String, identity: String, style: String, prompt: String) {
        viewModelScope.launch {
            val all = repo.personas.first()
            val old = all.firstOrNull { it.id == id } ?: return@launch
            if (old.builtin) {
                // 内置人格：仅允许改名，其余字段保持原样
                val updated = all.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it }
                repo.save(updated)
            } else {
                repo.upsert(
                    old.copy(
                        name = name.ifBlank { old.name },
                        identity = identity, style = style, prompt = prompt,
                    )
                )
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        val target = repo.personas.first().firstOrNull { it.id == id } ?: return@launch
        if (!target.builtin) repo.delete(id)
    }
}