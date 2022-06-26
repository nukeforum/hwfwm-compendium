package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.data.Essence
import com.mobile.wizardry.compendium.data.Rarity

class ManualEssenceProvider : EssenceProvider {
    private val sin = Essence.of(
        "sin",
        "Manifested essence of transgression",
        Rarity.Legendary
    )
    private val wind = Essence.of(
        "wind",
        "Manifested essence of wind",
        Rarity.Common
    )
    private val blood = Essence.of(
        "blood",
        "Manifested essence of blood",
        Rarity.Common
    )
    private val dark = Essence.of(
        "dark",
        "Manifested essence of darkness",
        Rarity.Uncommon
    )
    private val omen = Essence.of(
        "omen",
        "Manifested essence of premonition",
        rarity = Rarity.Epic
    )
    private val doom = Essence.of(
        "doom",
        setOf(sin, blood, dark),
        setOf(blood, dark, omen),
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
