package wizardry.compendium.statuseffect.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.StatusEffect
import javax.inject.Inject

@HiltViewModel
class StatusEffectSearchViewModel @Inject constructor(
    repository: StatusEffectRepository,
) : ViewModel() {
    private val effectsFlow = MutableStateFlow(emptyList<StatusEffect>())
    private val filterTermFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow<StatusEffectSearchFilter?>(null)

    private val _state = MutableStateFlow<StatusEffectSearchUiState>(StatusEffectSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(effectsFlow, filterTermFlow, filterFlow) { effects, term, filter ->
                StatusEffectSearchUiState.Success(
                    effects = effects.filter { e ->
                        e.name.contains(term, ignoreCase = true) &&
                            (filter == null || filter.predicate(e))
                    },
                    filterTerm = term,
                    appliedFilter = filter,
                )
            }.onEach { _state.emit(it) }.collect()
        }

        viewModelScope.launch {
            repository.statusEffects.collect { effectsFlow.emit(it) }
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }

    fun applyFilter(filter: StatusEffectSearchFilter?) {
        viewModelScope.launch { filterFlow.emit(filter) }
    }
}
