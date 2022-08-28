package com.mobile.wizardry.compendium.search

import com.mobile.wizardry.compendium.essences.model.Essence

data class SearchUiState(
    val essences: List<Essence>,
    val filterTerm: String,
    val appliedFilters: Collection<SearchFilter>
)
