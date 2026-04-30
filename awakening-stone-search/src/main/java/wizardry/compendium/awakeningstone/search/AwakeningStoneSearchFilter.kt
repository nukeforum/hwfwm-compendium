package wizardry.compendium.awakeningstone.search

import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity

sealed interface AwakeningStoneSearchFilter {
    val name: String get() = this::class.java.simpleName
    fun predicate(stone: AwakeningStone): Boolean

    object Common : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Common
    }

    object Uncommon : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Uncommon
    }

    object Rare : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Rare
    }

    object Epic : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Epic
    }

    object Legendary : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Legendary
    }

    object Unknown : AwakeningStoneSearchFilter {
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Unknown
    }

    companion object {
        val options = listOf(
            Common,
            Uncommon,
            Rare,
            Epic,
            Legendary,
            Unknown,
        )
    }
}
