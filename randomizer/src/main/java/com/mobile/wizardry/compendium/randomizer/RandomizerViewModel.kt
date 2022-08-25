package com.mobile.wizardry.compendium.randomizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.model.core.UiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class RandomizerViewModel
@Inject constructor(
    private val essenceProvider: EssenceProvider
) : ViewModel() {
    private val _essences = MutableStateFlow(emptyList<Essence>())
    private val essences get() = _essences.value
    private val manifestations = MutableStateFlow(emptyList<Essence.Manifestation>())
    private val confluences = MutableStateFlow(emptyList<Essence.Confluence>())

    private val _state = MutableStateFlow<UiResult<RandomizerUiState>>(UiResult.Loading)
    val state get() = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _essences.emit(essenceProvider.getEssences())
        }

        viewModelScope.launch(Dispatchers.IO) {
            _essences
                .filter { it.isNotEmpty() }
                .take(1)
                .collect {
                    manifestations.emit(it.filterIsInstance<Essence.Manifestation>())
                    confluences.emit(it.filterIsInstance<Essence.Confluence>())
                    RandomizerUiState(emptySet(), null).emit()
                }
        }
    }

    fun randomize() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            if (essences.isEmpty()) {
                _state.emit(UiResult.Error(IllegalStateException("No essences are available to randomize.")))
                return@launch
            }

            val set = mutableSetOf<Essence.Manifestation>()
            while (set.size < 3) {
                set.add(manifestations.value.random())
            }

            val confluence = confluences.value.find { it.confluenceSets.contains(ConfluenceSet(set)) }

            RandomizerUiState(set, confluence).emit()
        }
    }

    private suspend fun RandomizerUiState.emit() {
        _state.emit(UiResult.Success(this))
    }
}
