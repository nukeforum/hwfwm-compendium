package com.mobile.wizardry.compendium.essences.model

sealed interface Ability : Entity {
    override val name: String
    val effects: List<Effect>

    fun reportType(): String {
        return effects.map { it.type }.toSet().joinToString("/")
    }

    fun reportProperties(): String {
        return effects.flatMap { it.properties }.toSet().joinToString(",")
    }

    fun reportCost(): String {
        return effects.mapNotNull { effect -> effect.cost.takeIf { cost -> cost.isNotEmpty() } }
            .takeIf { it.size == 1 }
            ?.first()?.toString()
            ?: "Varies"
    }

    fun reportCooldown(): String {
        return effects.map { it.cooldown }.toSet()
            .takeIf { it.size == 1 }
            ?.first()?.toString()
            ?: "Varies"
    }

    data class Acquired(
        override val name: String,
        override val effects: List<Effect>,
        val rank: Rank,
        val tier: Int,
        val progress: Float,
        val boundEssence: Essence,
    ) : Ability

    data class Listing(
        override val name: String,
        override val effects: List<Effect>,
    ) : Ability {
        fun acquire(essence: Essence): Acquired {
            return Acquired(
                name = name,
                effects = effects.filter { it.rank == Rank.Iron },
                rank = Rank.Iron,
                tier = 0,
                progress = 0f,
                boundEssence = essence,
            )
        }

        fun rankUp(ability: Acquired): Acquired {
            return Acquired(
                name = name,
                effects = effects.filter { it.rank == Rank.next(ability.rank) },
                rank = Rank.next(ability.rank),
                tier = 0,
                progress = 0f,
                boundEssence = ability.boundEssence,
            )
        }
    }
}
