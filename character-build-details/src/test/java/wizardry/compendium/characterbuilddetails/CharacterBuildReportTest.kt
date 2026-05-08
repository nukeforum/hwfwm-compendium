package wizardry.compendium.characterbuilddetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity

class CharacterBuildReportTest {

    @Test
    fun `report has name and race header and empty slots`() {
        val build = CharacterBuild(name = "Jason", race = "Outworlder", racialAbilities = emptyList())
        val expected = """
            Jason
            Outworlder

            Rank: Unranked     Progression: 0%

            Power
              Essence: (none)

            Speed
              Essence: (none)

            Spirit
              Essence: (none)

            Recovery
              Essence: (none)

            Racial Abilities: (none)
        """.trimIndent()
        assertEquals(expected, build.report())
    }

    @Test
    fun `report renders an absorbed essence with its abilities`() {
        val sin = Essence.Manifestation(
            name = "Sin", rank = Rank.Unranked, rarity = Rarity.Unknown,
            properties = emptyList(), description = "", isRestricted = false,
        )
        val build = CharacterBuild(
            name = "Jason",
            race = "Outworlder",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(
                    essence = AbsorbedEssence(
                        essence = sin,
                        abilities = listOf(
                            Ability.Listing.of("Hand of the Reaper").acquire(sin),
                            Ability.Listing.of("Inflict Wound").acquire(sin),
                        ),
                    ),
                ),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )

        val lines = build.report().lines()
        val powerStart = lines.indexOf("Power")
        assertTrue("Power label should be present", powerStart >= 0)
        assertEquals("  Essence: Sin", lines[powerStart + 1])
        assertEquals("    Hand of the Reaper (Iron)", lines[powerStart + 2])
        assertEquals("    Inflict Wound (Iron)", lines[powerStart + 3])
    }

    @Test
    fun `report lists racial abilities by name`() {
        val build = CharacterBuild(
            name = "X", race = "Y",
            racialAbilities = listOf(
                Ability.Listing.of("Inflict Disease"),
                Ability.Listing.of("Object of Power"),
            ),
        )
        val text = build.report()
        assertTrue(text.contains("Racial Abilities"))
        assertTrue(text.contains("  Inflict Disease"))
        assertTrue(text.contains("  Object of Power"))
    }
}
