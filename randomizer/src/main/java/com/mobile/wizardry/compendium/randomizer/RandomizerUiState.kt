package com.mobile.wizardry.compendium.randomizer

import com.mobile.wizardry.compendium.essences.model.Essence

data class RandomizerUiState(
    val randomizedSet: Set<Essence.Manifestation>,
    val knownConfluence: Essence.Confluence?
)
