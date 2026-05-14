package wizardry.compendium.ability.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
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
                    cost = listOf(Cost.Upfront(amount = wizardry.compendium.domain.model.Amount.Low, resource = wizardry.compendium.domain.model.Resource.Mana)),
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
