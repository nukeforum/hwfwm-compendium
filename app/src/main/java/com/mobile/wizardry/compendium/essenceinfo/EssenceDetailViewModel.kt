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
    private val _state = MutableStateFlow<UiResult<EssenceDetailUiState>>(UiResult.Loading)
    val state = _state.asStateFlow()

    fun load(essenceHash: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            essenceProvider.getEssences().find { it.hashCode() == essenceHash }
                ?.let { essence ->
                    when(essence) {
                        is Essence.Manifestation -> buildManifestationState(essence)
                        is Essence.Confluence -> buildConfluenceState(essence)
                    }
                }
        }
    }

    fun load(essence: Essence) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(UiResult.Loading)

            when (essence) {
                is Essence.Manifestation -> buildManifestationState(essence)
                is Essence.Confluence -> buildConfluenceState(essence)
            }
        }
    }

    private suspend fun buildConfluenceState(essence: Essence.Confluence) {
        EssenceDetailUiState.ConfluenceUiState(essence).emit()
    }

    private suspend fun buildManifestationState(essence: Essence.Manifestation) {
        val confluences = essenceProvider.getEssences()
            .filterIsInstance<Essence.Confluence>()
            .filter { it.isProducedBy(essence) }

        EssenceDetailUiState.ManifestationUiState(
            essence,
            confluences
        ).emit()
    }

    private fun Essence.Confluence.isProducedBy(selectedEssence: Essence): Boolean {
        return confluenceSets.any { confluenceSet -> confluenceSet.set.any { essence -> essence == selectedEssence } }
    }

    private suspend fun EssenceDetailUiState.emit() {
        _state.emit(UiResult.Success(this))
    }
}
