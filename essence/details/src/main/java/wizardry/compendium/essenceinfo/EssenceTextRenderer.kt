package wizardry.compendium.essenceinfo

import wizardry.compendium.domain.model.Essence

object EssenceTextRenderer {

    fun renderAsText(essence: Essence): String = when (essence) {
        is Essence.Confluence -> "${essence.name} Confluence"
        is Essence.Manifestation -> """
            Item: [${essence.name} Essence]
            (${essence.rank.toString().lowercase()}, ${essence.rarity.toString().lowercase()})

            ${essence.description} (${essence.properties.joinToString(", ")}).

            Requirements: Less than 4 absorbed essences.

            ${essence.effects.joinToString { "Effect: ${it.description}" }}
        """.trimIndent()
        else -> essence.name
    }
}
