package wizardry.compendium.characterbuild.search

import wizardry.compendium.domain.model.CharacterBuild

sealed interface CharacterBuildSearchUiState {
    data object Loading : CharacterBuildSearchUiState

    data class Error(val exception: Exception) : CharacterBuildSearchUiState

    data class Success(
        val builds: List<CharacterBuild>,
        val filterTerm: String,
    ) : CharacterBuildSearchUiState
}
