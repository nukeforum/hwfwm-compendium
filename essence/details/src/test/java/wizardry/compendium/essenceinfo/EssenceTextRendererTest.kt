package wizardry.compendium.essenceinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity

class EssenceTextRendererTest {

    @Test
    fun `manifestation render includes name rank rarity description`() {
        val flame = Essence.of(
            name = "Flame",
            description = "fire essence",
            rarity = Rarity.Common,
            restricted = false,
        )

        val text = EssenceTextRenderer.renderAsText(flame)

        assertTrue("missing name in $text", text.contains("Flame Essence"))
        assertTrue("missing rank in $text", text.contains(flame.rank.toString().lowercase()))
        assertTrue("missing rarity in $text", text.contains(flame.rarity.toString().lowercase()))
        assertTrue("missing description in $text", text.contains("fire essence"))
        assertTrue("missing Requirements line in $text", text.contains("Requirements: Less than 4 absorbed essences."))
    }

    @Test
    fun `confluence render is name plus Confluence label`() {
        val wind = Essence.of("Wind", "", Rarity.Common, false)
        val blood = Essence.of("Blood", "", Rarity.Uncommon, false)
        val sin = Essence.of("Sin", "", Rarity.Legendary, false)
        val doom = Essence.of("Doom", restricted = false, ConfluenceSet(wind, blood, sin))

        assertEquals("Doom Confluence", EssenceTextRenderer.renderAsText(doom))
    }
}
