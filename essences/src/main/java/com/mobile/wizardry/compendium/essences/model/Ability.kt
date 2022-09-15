package com.mobile.wizardry.compendium.essences.model

sealed interface Ability : Entity {
    override val name: String
    val effects: List<Effect.AbilityEffect>

    data class Acquired
    internal constructor(
        override val name: String,
        override val effects: List<Effect.AbilityEffect>,
        val rank: Rank,
        val tier: Int,
        val progress: Float,
        val boundEssence: Essence,
        private val listing: Listing,
    ) : Ability {
        fun rankUp(): Acquired {
            return copy(
                effects = listing.effects.filter { it.rank.ordinal <= Rank.next(rank).ordinal },
                rank = Rank.next(rank),
                tier = 0,
                progress = 0f,
            )
        }
    }

    data class Listing(
        override val name: String,
        override val effects: List<Effect.AbilityEffect>,
    ) : Ability {
        fun acquire(essence: Essence): Acquired {
            return Acquired(
                name = name,
                effects = effects.filter { it.rank == Rank.Iron },
                rank = Rank.Iron,
                tier = 0,
                progress = 0f,
                boundEssence = essence,
                listing = this,
            )
        }
    }
}
