package com.mobile.wizardry.compendium.search

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface SearchUiState {
    data object Loading : SearchUiState

    data class Error(val exception: Exception) : SearchUiState

    data class Success(
        val essences: List<Essence>,
        val filterTerm: String,
        val appliedFilters: Collection<SearchFilter>
    ) : SearchUiState
}
