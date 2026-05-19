package wizardry.compendium.awakeningstone.search

import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity

sealed interface AwakeningStoneSearchFilter {
    val name: String
    fun predicate(stone: AwakeningStone): Boolean

    object Common : AwakeningStoneSearchFilter {
        override val name = "Common"
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Common
    }

    object Uncommon : AwakeningStoneSearchFilter {
        override val name = "Uncommon"
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Uncommon
    }

    object Rare : AwakeningStoneSearchFilter {
        override val name = "Rare"
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Rare
    }

    object Epic : AwakeningStoneSearchFilter {
        override val name = "Epic"
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Epic
    }

    object Legendary : AwakeningStoneSearchFilter {
        override val name = "Legendary"
        override fun predicate(stone: AwakeningStone) = stone.rarity == Rarity.Legendary
    }

    object Unknown : AwakeningStoneSearchFilter {
        override val name = "Unknown"
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
