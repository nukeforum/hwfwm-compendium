package com.mobile.wizardry.compendium.essences.model

import kotlin.time.Duration

sealed interface Effect {
    val rank: Rank
    val properties: List<Property>
    val cost: List<Cost>

    /**
     * in seconds
     */
    val cooldown: Duration
    val description: String

    data class AbilityEffect(
        override val rank: Rank,
        val type: AbilityType,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Duration,
        override val description: String,
        val replacementKey: String? = null,
    ) : Effect {
        override fun toString(): String = description
    }

    data class StatusEffect(
        override val rank: Rank,
        val type: StatusType,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Duration,
        override val description: String,
    ) : Effect {
        override fun toString(): String = description
    }

    data class ItemEffect(
        override val rank: Rank,
        override val properties: List<Property>,
        override val cost: List<Cost>,
        override val cooldown: Duration,
        override val description: String,
    ) : Effect {
        override fun toString(): String = description
    }
}
