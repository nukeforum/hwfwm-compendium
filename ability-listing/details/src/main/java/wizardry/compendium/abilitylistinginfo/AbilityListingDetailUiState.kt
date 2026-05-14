package wizardry.compendium.abilitylistinginfo

import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.StatusEffect

sealed interface AbilityListingDetailUiState {
    data object Loading : AbilityListingDetailUiState

    data class Error(val exception: Exception) : AbilityListingDetailUiState

    data class Success(
        val listing: Ability.Listing,
        val isContribution: Boolean,
        val statusEffects: List<StatusEffect>,
        val selectedRank: Rank? = null,
    ) : AbilityListingDetailUiState
}
