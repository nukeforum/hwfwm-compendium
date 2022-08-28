package com.mobile.wizardry.compendium.essenceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.model.core.UiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EssenceDetailViewModel
@Inject constructor(
    private val essenceProvider: EssenceProvider,
) : ViewModel() {
    private val history = ArrayDeque<Essence>()
    private val _state = MutableStateFlow<UiResult<EssenceDetailUiState>>(UiResult.Loading)
    val state = _state.asStateFlow()

    fun load(essenceHash: Int) {
        currentlyLoadedEssence?.let { history.addFirst(it) }
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            essenceProvider.getEssences().find { it.hashCode() == essenceHash }
                ?.let { essence -> buildState(essence) }
                ?: _state.emit(UiResult.Error(IllegalArgumentException("no essence found with hash: $essenceHash")))
        }
    }

    fun load(essence: Essence) {
        currentlyLoadedEssence?.let { history.addFirst(it) }
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            buildState(essence)
        }
    }

    private suspend fun buildState(essence: Essence) {
        when (essence) {
            is Essence.Manifestation -> buildManifestationState(essence)
            is Essence.Confluence -> buildConfluenceState(essence)
        }
    }

    private suspend fun buildConfluenceState(essence: Essence.Confluence) {
        EssenceDetailUiState.ConfluenceUiState(essence, history.firstOrNull() as? Essence.Manifestation).emit()
    }

    private suspend fun buildManifestationState(essence: Essence.Manifestation) {
        val confluences = essenceProvider.getEssences()
            .filterIsInstance<Essence.Confluence>()
            .filter { it.isProducedBy(essence) }

        EssenceDetailUiState.ManifestationUiState(
            essence,
            history.firstOrNull() as? Essence.Confluence,
            confluences
        ).emit()
    }

    private fun Essence.Confluence.isProducedBy(selectedEssence: Essence): Boolean {
        return confluenceSets.any { confluenceSet -> confluenceSet.set.any { essence -> essence == selectedEssence } }
    }

    private suspend fun EssenceDetailUiState.emit() {
        _state.emit(UiResult.Success(this))
    }

    private val currentlyLoadedEssence: Essence?
        get() {
            return state.value.takeIf { it is UiResult.Success }?.data?.essence
        }

    fun goBack() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val essence = history.removeFirst()) {
                is Essence.Manifestation -> buildManifestationState(essence)
                is Essence.Confluence -> buildConfluenceState(essence)
            }
        }
    }
}
