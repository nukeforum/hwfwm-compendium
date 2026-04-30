package wizardry.compendium.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Essence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
@Inject constructor(
    essenceRepository: EssenceRepository,
) : ViewModel() {
    private val essencesFlow = MutableStateFlow(emptyList<Essence>())
    private val filtersFlow = MutableStateFlow(SearchFilter.options.associateBy { it.name })
    private val filterTermFlow = MutableStateFlow("")

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                essencesFlow,
                filterTermFlow,
                filtersFlow.map { it.values }
            ) { essences, filterTerm, filters ->
                SearchUiState.Success(
                    essences.filterWith(filterTerm, filters),
                    filterTerm,
                    filters
                )
            }
                .onEach { _state.emit(it) }
                .collect()
        }

        viewModelScope.launch(Dispatchers.IO) {
            essenceRepository.essences.collect { essencesFlow.emit(it) }
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }

    fun applyFilter(filter: SearchFilter) {
        viewModelScope.launch {
            filtersFlow.value.toMutableMap()
                .apply {
                    if (containsKey(filter.name)) {
                        remove(filter.name)
                    } else {
                        set(filter.name, filter)
                    }
                }
                .also { Log.d("chaser", it.values.joinToString { it.name }) }
                .also { filtersFlow.emit(it) }
        }
    }

    private fun List<Essence>.filterWith(
        term: String,
        filters: Collection<SearchFilter>
    ): List<Essence> = mapNotNull { essence ->
        essence.takeIf {
            essence.name.lowercase().contains(term.lowercase())
                    && filters.any { filter -> filter.predicate(essence) }
        }
    }
}
