package com.mobile.wizardry.compendium.essences.model

import java.util.*

interface Essence : Entity {
    val isRestricted: Boolean

    data class Confluence(
        override val name: String,
        val confluenceSets: Set<ConfluenceSet>,
        override val isRestricted: Boolean,
    ) : Essence

    data class Manifestation(
        override val name: String,
        override val rank: Rank,
        override val rarity: Rarity,
        override val properties: List<Property>,
        override val effects: List<Effect>,
        override val description: String,
        override val isRestricted: Boolean,
    ) : Essence, Item

    companion object {
        fun of(
            name: String,
            description: String,
            rarity: Rarity,
            restricted: Boolean,
        ): Manifestation {
            val titleCaseName = name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }

            return Manifestation(
                titleCaseName,
                Rank.Unranked,
                rarity,
                listOf(Property.Consumable, Property.Essence),
                listOf(
                    Effect.ItemEffect(
                        rank = Rank.Unranked,
                        properties = emptyList(),
                        cost = emptyList(),
                        cooldown = 0,
                        description = "Imbues 1 awakened ${name.lowercase()} essence ability and 4 unawakened ${name.lowercase()} essence abilities"
                    )
                ),
                description,
                restricted,
            )
        }

        fun of(
            name: String,
            restricted: Boolean,
            vararg confluences: ConfluenceSet
        ): Confluence {
            check(confluences.isNotEmpty() && confluences.all { it.set.size == 3 }) {
                "Confluence Essences cannot be created without a set of Essences that produce it." +
                        "Provided essences:\n${confluences.joinToString("\n")}"
            }

            return Confluence(name, confluences.toSet(), restricted)
        }
    }
}
