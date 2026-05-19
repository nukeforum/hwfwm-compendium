package wizardry.compendium.search

import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity

sealed interface SearchFilter {
    val name: String
    fun predicate(essence: Essence): Boolean

    object Confluence : SearchFilter {
        override val name = "Confluence"
        override fun predicate(essence: Essence): Boolean {
            return essence is Essence.Confluence
        }
    }

    object Common : SearchFilter {
        override val name = "Common"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Common
        }
    }

    object Uncommon : SearchFilter {
        override val name = "Uncommon"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Uncommon
        }
    }

    object Rare : SearchFilter {
        override val name = "Rare"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Rare
        }
    }

    object Epic : SearchFilter {
        override val name = "Epic"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Epic
        }
    }

    object Legendary : SearchFilter {
        override val name = "Legendary"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Legendary
        }
    }

    object Unknown : SearchFilter {
        override val name = "Unknown"
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Unknown
        }
    }

    companion object {
        val options = listOf(
            Confluence,
            Common,
            Uncommon,
            Rare,
            Epic,
            Legendary,
            Unknown,
        )
    }
}
