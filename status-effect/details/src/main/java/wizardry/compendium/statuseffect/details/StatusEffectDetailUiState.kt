package wizardry.compendium.statuseffect.details

import wizardry.compendium.domain.model.StatusEffect

sealed interface StatusEffectDetailUiState {
    data object Loading : StatusEffectDetailUiState
    data class Error(val exception: Exception) : StatusEffectDetailUiState
    data class Success(
        val effect: StatusEffect,
        val isContribution: Boolean,
    ) : StatusEffectDetailUiState
}
