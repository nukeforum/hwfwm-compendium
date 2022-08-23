package com.mobile.wizardry.compendium.randomizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.model.core.UiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RandomizerViewModel
@Inject constructor(
    private val essenceProvider: EssenceProvider
) : ViewModel() {
    private val _state = MutableStateFlow<UiResult<RandomizerUiState>>(UiResult.Loading)
    val state get() = _state.asStateFlow()

    init {
        randomize()
    }

    fun randomize() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            val essences = essenceProvider.getEssences()

            if (essences.isEmpty()) {
                _state.emit(UiResult.Error(IllegalStateException("No essences are available to randomize.")))
                return@launch
            }

            val (manifestations, confluences) = essences.groupBy { it.javaClass.simpleName }
                .let { Pair(it[Essence.Manifestation::class.java.simpleName]!!, it[Essence.Confluence::class.java.simpleName]!!) }

            val set = mutableSetOf<Essence.Manifestation>()
            while (set.size < 3) {
                val manifestation = manifestations.random()
                if (manifestation !is Essence.Manifestation) continue
                set.add(manifestation)
            }

            val confluenceSet = ConfluenceSet(set)

            val confluence = confluences.find { (it as? Essence.Confluence)?.confluenceSets?.contains(confluenceSet) == true }

            _state.emit(
                UiResult.Success(
                    RandomizerUiState(set, confluence as? Essence.Confluence)
                )
            )
        }
    }
}
