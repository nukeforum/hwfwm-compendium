package wizardry.compendium.ability.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import kotlin.time.Duration

class AbilityTextRendererTest {

    @Test
    fun `renderBuild produces expected output for a simple build`() {
        val wind = Essence.Manifestation(
            name = "Wind", rank = Rank.Iron, rarity = Rarity.Common,
            properties = emptyList(), description = "", isRestricted = false,
        )
        val frostBolt = Ability.Listing(
            name = "Frost Bolt",
            effects = listOf(
                Effect.AbilityEffect(
                    rank = Rank.Iron,
                    type = AbilityType.SpecialAttack,
                    properties = listOf(Property.Ice),
                    cost = listOf(Cost.Upfront(amount = wizardry.compendium.essences.model.Amount.Low, resource = wizardry.compendium.essences.model.Resource.Mana)),
                    cooldown = Duration.ZERO,
                    description = "A bolt of ice.",
                    replacementKey = null,
                ),
            ),
        )
        val build = CharacterBuild(
            name = "Frosty",
            race = "Human",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(wind, listOf(frostBolt.acquire(wind)))),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )

        val text = AbilityTextRenderer.renderBuild(build, statusEffects = emptyList())

        // Shape-based asserts (the derived rank/progression depend on subtle model
        // behavior; don't snapshot the exact percentage):
        assertTrue("starts with name+race", text.startsWith("Frosty\nHuman"))
        assertTrue("has Power slot header", text.contains("\nPower\n"))
        assertTrue("has Speed empty marker", text.contains("Speed\nEssence: (none)"))
        assertTrue("has Spirit empty marker", text.contains("Spirit\nEssence: (none)"))
        assertTrue("has Recovery empty marker", text.contains("Recovery\nEssence: (none)"))
        assertTrue("names the essence", text.contains("Essence: Wind"))
        assertTrue("names the ability", text.contains("Frost Bolt"))
        assertTrue("renders the description", text.contains("A bolt of ice."))
        assertTrue("trailing racial section", text.endsWith("Racial Abilities: (none)"))
    }
}
