package wizardry.compendium.racetemplate.search

import wizardry.compendium.domain.model.RaceTemplate

sealed interface RaceTemplateSearchUiState {
    data object Loading : RaceTemplateSearchUiState

    data class Error(val exception: Exception) : RaceTemplateSearchUiState

    data class Success(
        val templates: List<RaceTemplate>,
        val filterTerm: String,
    ) : RaceTemplateSearchUiState
}
