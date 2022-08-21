package com.mobile.wizardry.compendium.search

import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity

sealed interface SearchFilter {
    val name: String get() = this::class.java.simpleName
    fun predicate(essence: Essence): Boolean

    object Confluence : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return essence is Essence.Confluence
        }
    }

    object Common : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Common
        }
    }

    object Uncommon : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Uncommon
        }
    }

    object Rare : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Rare
        }
    }

    object Epic : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Epic
        }
    }

    object Legendary : SearchFilter {
        override fun predicate(essence: Essence): Boolean {
            return (essence as? Essence.Manifestation)?.rarity == Rarity.Legendary
        }
    }

    object Unknown : SearchFilter {
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
        )
    }
}
