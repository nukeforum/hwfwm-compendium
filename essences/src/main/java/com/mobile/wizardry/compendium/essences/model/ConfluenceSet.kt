package com.mobile.wizardry.compendium.essences.model

data class ConfluenceSet(
    val set: Set<Essence.Manifestation>,
    val isRestricted: Boolean = false,
) {
    constructor(
        first: Essence.Manifestation,
        second: Essence.Manifestation,
        third: Essence.Manifestation,
        restricted: Boolean = false
    ) : this(setOf(first, second, third), restricted)
}
