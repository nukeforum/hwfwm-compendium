package com.mobile.wizardry.compendium.data

import java.util.*

data class Essence(
    override val name: String,
    override val rank: Rank,
    override val rarity: Rarity,
    override val properties: List<Property>,
    override val effects: List<Effect>,
    override val description: String,
    val confluences: Set<Set<Essence>> = emptySet()
) : Item {
    init {
        if (
            confluences.isNotEmpty()
            && confluences.any { it.size != 3 }
        ) throw IllegalArgumentException("confluence must contain precisely three Essence.")
    }

    companion object {
        fun of(
            name: String,
            description: String,
            rarity: Rarity,
        ): Essence {
            val titleCaseName = name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
            return Essence(
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
            vararg confluences: Set<Essence>
        ): Essence {
            return of(
                name = name,
                description = "",
                rarity = Rarity.Unknown
            )
                .copy(confluences = confluences.toSet())
        }
    }
}
