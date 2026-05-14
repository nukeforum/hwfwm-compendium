package wizardry.compendium.characterbuilddetails

import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.StatusEffect

sealed interface CharacterBuildDetailUiState {
    data object Loading : CharacterBuildDetailUiState
    data class Error(val exception: Exception) : CharacterBuildDetailUiState
    data class Success(
        val build: CharacterBuild,
        val statusEffects: List<StatusEffect> = emptyList(),
    ) : CharacterBuildDetailUiState
}
