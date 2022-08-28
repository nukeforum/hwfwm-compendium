package com.mobile.wizardry.compendium.essences.model

data class Effect(
    val rank: Rank,
    val type: AbilityType,
    val properties: List<Property>,
    val cost: List<AbilityCost>,
    /**
     * in seconds
     */
    val cooldown: Int,
    val description: String,
) {
    override fun toString(): String = description
}
