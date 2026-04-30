package com.mobile.wizardry.compendium.awakeningstone.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.essences.AwakeningStoneProvider
import com.mobile.wizardry.compendium.essences.model.AwakeningStone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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

    private val _state = MutableStateFlow<AwakeningStoneSearchUiState>(AwakeningStoneSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(stonesFlow, filterTermFlow) { stones, filterTerm ->
                AwakeningStoneSearchUiState.Success(
                    stones = stones.filterWith(filterTerm),
                    filterTerm = filterTerm,
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

    private fun List<AwakeningStone>.filterWith(term: String): List<AwakeningStone> =
        filter { it.name.contains(term, ignoreCase = true) }
}
