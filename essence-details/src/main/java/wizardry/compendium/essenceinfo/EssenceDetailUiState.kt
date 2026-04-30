package wizardry.compendium.essenceinfo

import wizardry.compendium.essences.model.Essence

sealed interface EssenceDetailUiState {
    data object Loading : EssenceDetailUiState

    data class Error(val exception: Exception) : EssenceDetailUiState

    sealed interface Success : EssenceDetailUiState {
        val essence: Essence
        val previousEssence: Essence?
        val isContribution: Boolean

        data class ManifestationUiState(
            override val essence: Essence.Manifestation,
            override val previousEssence: Essence.Confluence?,
            override val isContribution: Boolean,
            val knownConfluences: List<Essence.Confluence>
        ) : Success

        data class ConfluenceUiState(
            override val essence: Essence.Confluence,
            override val previousEssence: Essence.Manifestation?,
            override val isContribution: Boolean,
        ) : Success
    }
}
