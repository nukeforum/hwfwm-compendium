package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity

class ManualEssenceProvider : EssenceProvider {
    private val sin = Essence.of(
        name = "sin",
        description = "Manifested essence of transgression",
        Rarity.Legendary,
        restricted = false,
    )
    private val wind = Essence.of(
        name = "wind",
        description = "Manifested essence of wind",
        Rarity.Common,
        restricted = false,
    )
    private val blood = Essence.of(
        name = "blood",
        description = "Manifested essence of blood",
        Rarity.Common,
        restricted = false,
    )
    private val dark = Essence.of(
        name = "dark",
        description = "Manifested essence of darkness",
        Rarity.Uncommon,
        restricted = false,
    )
    private val omen = Essence.of(
        name = "omen",
        description = "Manifested essence of premonition",
        rarity = Rarity.Epic,
        restricted = false,
    )
    private val doom = Essence.of(
        name = "doom",
        restricted = false,
        ConfluenceSet(sin, blood, dark),
        ConfluenceSet(blood, dark, omen),
    )

    override suspend fun getEssences(): List<Essence> {
        return listOf(
            sin,
            wind,
            blood,
            dark,
            doom,
            omen,
        )
    }
}
