package com.mobile.wizardry.compendium.search

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
    private val transformersFlow = MutableStateFlow(emptyMap<String, SearchTransformer>())

    private val _state = MutableStateFlow<UiResult<SearchUiState>>(UiResult.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            essencesFlow
                .combine(transformersFlow.map { it.values }) { essences, transformers ->
                    val transformed = essences.transformWith(transformers)
                    SearchUiState(transformed, transformers.getFilterTerm())
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

    fun setFilter(filter: String) {
        viewModelScope.launch {
            transformersFlow.value.toMutableMap()
                .apply { set(SearchTransformer.Filter::class.java.simpleName, SearchTransformer.Filter(filter)) }
                .emit()
        }
    }

    fun setShowConfluences(showConfluences: Boolean) {
        viewModelScope.launch {
            transformersFlow.value.toMutableMap()
                .apply {
                    if (showConfluences) {
                        remove(SearchTransformer.HideConfluences.name)
                    } else {
                        set(SearchTransformer.HideConfluences.name, SearchTransformer.HideConfluences)
                    }
                }
                .emit()
        }
    }

    private fun List<Essence>.transformWith(
        transformers: Collection<SearchTransformer>
    ): List<Essence> = mapNotNull { essence ->
        essence.takeIf {
            transformers.all { transformer -> transformer.predicate(essence) }
        }
    }

    private suspend fun List<Essence>.emit() {
        essencesFlow.emit(this)
    }

    private suspend fun Map<String, SearchTransformer>.emit() {
        transformersFlow.emit(this)
    }

    private suspend fun SearchUiState.emit() {
        _state.emit(UiResult.Success(this))
    }
}
