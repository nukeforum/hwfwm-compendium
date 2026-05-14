package wizardry.compendium.statuseffect.search

import wizardry.compendium.essences.model.StatusEffect

sealed interface StatusEffectSearchUiState {
    data object Loading : StatusEffectSearchUiState
    data class Error(val exception: Exception) : StatusEffectSearchUiState
    data class Success(
        val effects: List<StatusEffect>,
        val filterTerm: String,
        val appliedFilter: StatusEffectSearchFilter?,
    ) : StatusEffectSearchUiState
}
