package wizardry.compendium.essences.model

import java.util.Locale

data class AwakeningStone(
    override val name: String,
    override val rank: Rank,
    override val rarity: Rarity,
    override val properties: List<Property>,
    override val effects: List<Effect>,
    override val description: String,
) : Item {
    companion object {
        fun of(name: String, rarity: Rarity): AwakeningStone {
            val titleCaseName = name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
            return AwakeningStone(
                name = titleCaseName,
                rank = Rank.Unranked,
                rarity = rarity,
                properties = listOf(Property.Consumable),
                effects = emptyList(),
                description = "none",
            )
        }
    }
}
