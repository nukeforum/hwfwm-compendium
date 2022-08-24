package com.mobile.wizardry.compendium.essenceinfo

import com.mobile.wizardry.compendium.essences.model.Essence

sealed interface EssenceDetailUiState {
    val essence: Essence

    data class ManifestationUiState(
        override val essence: Essence.Manifestation,
        val knownConfluences: List<Essence.Confluence>
    ) : EssenceDetailUiState

    data class ConfluenceUiState(
        override val essence: Essence.Confluence,
    ) : EssenceDetailUiState
}
