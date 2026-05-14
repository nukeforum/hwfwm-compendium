package wizardry.compendium.abilitylisting.search

import wizardry.compendium.essences.model.Ability

sealed interface AbilityListingSearchUiState {
    data object Loading : AbilityListingSearchUiState

    data class Error(val exception: Exception) : AbilityListingSearchUiState

    data class Success(
        val listings: List<Ability.Listing>,
        val filterTerm: String,
    ) : AbilityListingSearchUiState
}
