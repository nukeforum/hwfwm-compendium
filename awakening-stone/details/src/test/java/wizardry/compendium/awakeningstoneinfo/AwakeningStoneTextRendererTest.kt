package wizardry.compendium.awakeningstoneinfo

import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity

class AwakeningStoneTextRendererTest {

    @Test
    fun `render includes name rank and rarity`() {
        val volcano = AwakeningStone.of("Volcano", Rarity.Epic)

        val text = AwakeningStoneTextRenderer.renderAsText(volcano)

        assertTrue("missing name in $text", text.contains("Volcano Awakening Stone"))
        assertTrue("missing rank in $text", text.contains(volcano.rank.toString().lowercase()))
        assertTrue("missing rarity in $text", text.contains(volcano.rarity.toString().lowercase()))
    }
}
