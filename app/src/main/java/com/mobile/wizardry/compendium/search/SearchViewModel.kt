package com.mobile.wizardry.compendium.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.UiResult
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.Essence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
@Inject constructor(
    private val essenceProvider: EssenceProvider
) : ViewModel() {
    private val essencesFlow = MutableStateFlow(emptyList<Essence>())
    private val filtersFlow = MutableStateFlow(SearchFilter.options.associateBy { it.name })
    private val filterTermFlow = MutableStateFlow("")

    private val _state = MutableStateFlow<UiResult<SearchUiState>>(UiResult.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                essencesFlow,
                filterTermFlow,
                filtersFlow.map { it.values }
            ) { essences, filterTerm, filters ->
                SearchUiState(
                    essences.filterWith(filterTerm, filters),
                    filterTerm,
                    filters
                )
            }
                .onEach {
                    if (_state.value !is UiResult.Loading || it.essences.isNotEmpty()) {
                        it.emit()
                    }
                }
                .collect()
        }

        viewModelScope.launch {
            essenceProvider.getEssences().emit()
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
                .emit()
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

    private suspend fun List<Essence>.emit() {
        essencesFlow.emit(this)
    }

    private suspend fun Map<String, SearchFilter>.emit() {
        filtersFlow.emit(this)
    }

    private suspend fun SearchUiState.emit() {
        _state.emit(UiResult.Success(this))
    }
}
