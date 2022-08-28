package com.mobile.wizardry.compendium.essences.model

sealed interface Ability : Entity {
    override val name: String
    val abilityType: AbilityType
    val properties: List<Property>
    val cost: AbilityCost
    /**
     * In seconds.
     */
    val cooldown: Int
    val effects: List<Effect>

    data class Acquired(
        override val name: String,
        override val abilityType: AbilityType,
        override val properties: List<Property>,
        override val cost: AbilityCost,
        override val cooldown: Int,
        override val effects: List<Effect>,
        val rank: Rank,
        val tier: Int,
        val progress: Float,
        val boundEssence: Essence,
    ) : Ability

    data class Listing(
        override val name: String,
        override val abilityType: AbilityType,
        override val properties: List<Property>,
        override val cost: AbilityCost,
        override val cooldown: Int,
        override val effects: List<Effect>
    ) : Ability

    /**
     * Use for mutating ability to a given rank
     */
    abstract class RankDecorator(val ability: Ability, val rank: Rank) : Ability
}
