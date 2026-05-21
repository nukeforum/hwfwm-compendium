package wizardry.compendium.awakeningstoneinfo

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity

class AwakeningStoneTextRendererTest {

    @Test
    fun `factory-shaped stone includes name rank and rarity`() {
        val volcano = AwakeningStone.of("Volcano", Rarity.Epic)

        val text = AwakeningStoneTextRenderer.renderAsText(volcano)

        assertTrue("missing name in $text", text.contains("Volcano Awakening Stone"))
        assertTrue("missing rank in $text", text.contains(volcano.rank.toString().lowercase()))
        assertTrue("missing rarity in $text", text.contains(volcano.rarity.toString().lowercase()))
    }

    @Test
    fun `fully-populated stone renders description properties and every effect`() {
        val stone = AwakeningStone(
            name = "Stoneskin",
            rank = Rank.Bronze,
            rarity = Rarity.Rare,
            properties = listOf(Property.Consumable, Property.Fire),
            description = "Hardens the user's skin.",
            effects = listOf(
                Effect.ItemEffect(
                    rank = Rank.Bronze,
                    properties = emptyList(),
                    cost = emptyList(),
                    cooldown = Duration.ZERO,
                    description = "+10% physical resistance for 30s.",
                ),
                Effect.ItemEffect(
                    rank = Rank.Bronze,
                    properties = emptyList(),
                    cost = emptyList(),
                    cooldown = Duration.ZERO,
                    description = "Reflects 5% of incoming damage.",
                ),
            ),
        )

        val text = AwakeningStoneTextRenderer.renderAsText(stone)

        assertTrue("missing description in $text", text.contains("Hardens the user's skin."))
        assertTrue("missing properties block in $text", text.contains("consumable, fire"))
        assertTrue("missing first effect in $text", text.contains("Effect: +10% physical resistance for 30s."))
        assertTrue("missing second effect in $text", text.contains("Effect: Reflects 5% of incoming damage."))
    }
}
