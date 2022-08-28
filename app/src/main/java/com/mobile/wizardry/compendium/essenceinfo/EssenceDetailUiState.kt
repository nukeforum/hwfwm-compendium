package com.mobile.wizardry.compendium.essenceinfo

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface EssenceDetailUiState {
    val essence: Essence
    val previousEssence: Essence?

    data class ManifestationUiState(
        override val essence: Essence.Manifestation,
        override val previousEssence: Essence.Confluence?,
        val knownConfluences: List<Essence.Confluence>
    ) : EssenceDetailUiState

    data class ConfluenceUiState(
        override val essence: Essence.Confluence,
        override val previousEssence: Essence.Manifestation?,
    ) : EssenceDetailUiState
}
