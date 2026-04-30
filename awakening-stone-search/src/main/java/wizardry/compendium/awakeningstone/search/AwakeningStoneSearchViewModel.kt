package wizardry.compendium.awakeningstone.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.AwakeningStoneProvider
import wizardry.compendium.essences.model.AwakeningStone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneSearchViewModel
@Inject constructor(
    private val provider: AwakeningStoneProvider,
) : ViewModel() {
    private val stonesFlow = MutableStateFlow(emptyList<AwakeningStone>())
    private val filterTermFlow = MutableStateFlow("")
    private val filtersFlow = MutableStateFlow(
        AwakeningStoneSearchFilter.options.associateBy { it.name }
    )

    private val _state = MutableStateFlow<AwakeningStoneSearchUiState>(AwakeningStoneSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                stonesFlow,
                filterTermFlow,
                filtersFlow.map { it.values }
            ) { stones, filterTerm, filters ->
                AwakeningStoneSearchUiState.Success(
                    stones = stones.filterWith(filterTerm, filters),
                    filterTerm = filterTerm,
                    appliedFilters = filters,
                )
            }
                .onEach { _state.emit(it) }
                .collect()
        }

        viewModelScope.launch(Dispatchers.IO) {
            stonesFlow.emit(provider.getAwakeningStones())
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }

    fun applyFilter(filter: AwakeningStoneSearchFilter) {
        viewModelScope.launch {
            filtersFlow.emit(
                filtersFlow.value.toMutableMap().apply {
                    if (containsKey(filter.name)) remove(filter.name)
                    else set(filter.name, filter)
                }
            )
        }
    }

    private fun List<AwakeningStone>.filterWith(
        term: String,
        filters: Collection<AwakeningStoneSearchFilter>,
    ): List<AwakeningStone> = filter { stone ->
        stone.name.contains(term, ignoreCase = true)
                && filters.any { it.predicate(stone) }
    }
}
