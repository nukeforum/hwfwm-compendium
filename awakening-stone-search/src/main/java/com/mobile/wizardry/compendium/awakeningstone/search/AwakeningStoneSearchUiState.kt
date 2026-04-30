package com.mobile.wizardry.compendium.awakeningstone.search

import com.mobile.wizardry.compendium.essences.model.AwakeningStone

sealed interface AwakeningStoneSearchUiState {
    data object Loading : AwakeningStoneSearchUiState

    data class Error(val exception: Exception) : AwakeningStoneSearchUiState

    data class Success(
        val stones: List<AwakeningStone>,
        val filterTerm: String,
    ) : AwakeningStoneSearchUiState
}
