package wizardry.compendium.abilitylistinginfo

import wizardry.compendium.essences.model.Ability

sealed interface AbilityListingDetailUiState {
    data object Loading : AbilityListingDetailUiState

    data class Error(val exception: Exception) : AbilityListingDetailUiState

    data class Success(
        val listing: Ability.Listing,
        val isContribution: Boolean,
    ) : AbilityListingDetailUiState
}
