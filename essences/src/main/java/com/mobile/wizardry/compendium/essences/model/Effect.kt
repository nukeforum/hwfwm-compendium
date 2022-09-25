package com.mobile.wizardry.compendium.essences.model

sealed interface Effect {
    val rank: Rank
    val properties: List<Property>
    val cost: List<Cost>

    /**
     * in seconds
     */
    val cooldown: Int
    val description: String

    data class AbilityEffect(
        override val rank: Rank,
        val type: AbilityType,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Int,
        override val description: String,
    ) : Effect {
        override fun toString(): String = description
    }

    data class StatusEffect(
        override val rank: Rank,
        val type: StatusType,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Int,
        override val description: String,
    ) : Effect {
        override fun toString(): String = description
    }

    data class ItemEffect(
        override val rank: Rank,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Int,
        override val description: String,
    ) : Effect {
        override fun toString(): String = description
    }
}
