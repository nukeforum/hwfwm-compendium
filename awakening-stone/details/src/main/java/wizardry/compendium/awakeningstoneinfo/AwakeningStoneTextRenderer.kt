package wizardry.compendium.awakeningstoneinfo

import wizardry.compendium.domain.model.AwakeningStone

object AwakeningStoneTextRenderer {

    fun renderAsText(stone: AwakeningStone): String = """
        Item: [${stone.name} Awakening Stone]
        (${stone.rank.toString().lowercase()}, ${stone.rarity.toString().lowercase()})

        ${stone.description} (${stone.properties.joinToString(", ")}).

        ${stone.effects.joinToString { "Effect: ${it.description}" }}
    """.trimIndent()
}
