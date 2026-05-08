package wizardry.compendium.characterbuilddetails

import wizardry.compendium.essences.model.CharacterBuild

sealed interface CharacterBuildDetailUiState {
    data object Loading : CharacterBuildDetailUiState
    data class Error(val exception: Exception) : CharacterBuildDetailUiState
    data class Success(val build: CharacterBuild) : CharacterBuildDetailUiState
}
