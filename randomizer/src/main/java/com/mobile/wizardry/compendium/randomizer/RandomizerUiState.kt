package com.mobile.wizardry.compendium.randomizer

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface RandomizerUiState {
    object Loading : RandomizerUiState

    data class Error(val exception: Throwable) : RandomizerUiState

    data class Success(
        val randomizedSet: Set<Essence.Manifestation>,
        val knownConfluence: Essence.Confluence?
    ) : RandomizerUiState
}
