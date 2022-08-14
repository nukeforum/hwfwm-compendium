package com.mobile.wizardry.compendium.essences.model

import java.util.*

interface Essence : Entity {
    data class Confluence(
        override val name: String,
        val confluenceSets: Set<Set<Essence.Manifestation>>
    ) : Essence

    data class Manifestation(
        override val name: String,
        override val rank: Rank,
        override val rarity: Rarity,
        override val properties: List<Property>,
        override val effects: List<Effect>,
        override val description: String
    ) : Essence, Item

    companion object {
        fun of(
            name: String,
            description: String,
            rarity: Rarity,
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
                    Effect("Imbues 1 awakened ${name.lowercase()} essence ability and 4 unawakened ${name.lowercase()} essence abilities")
                ),
                description,
            )
        }

        fun of(
            name: String,
            vararg confluences: Set<Manifestation>
        ): Confluence {
            check(confluences.isNotEmpty()) {
                "Confluence Essences cannot be created without a set of Essences that produce it."
            }

            check(confluences.all { it.size == 3 }) {
                "Confluence sets must contain precisely three Essences."
            }

            return Confluence(name, confluences.toSet())
        }
    }
}
