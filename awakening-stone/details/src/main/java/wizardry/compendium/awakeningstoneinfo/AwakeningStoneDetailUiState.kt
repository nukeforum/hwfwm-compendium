package wizardry.compendium.awakeningstoneinfo

import wizardry.compendium.domain.model.AwakeningStone

sealed interface AwakeningStoneDetailUiState {
    data object Loading : AwakeningStoneDetailUiState

    data class Error(val exception: Exception) : AwakeningStoneDetailUiState

    data class Success(
        val stone: AwakeningStone,
        val isContribution: Boolean,
    ) : AwakeningStoneDetailUiState
}
