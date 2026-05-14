package wizardry.compendium.domain.model

interface Item : Entity {
    val rank: Rank
    val rarity: Rarity
    val properties: List<Property>
    val effects: List<Effect>
    val description: String
}
