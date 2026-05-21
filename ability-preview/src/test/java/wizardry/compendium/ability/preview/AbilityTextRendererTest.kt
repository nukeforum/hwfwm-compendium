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
import wizardry.compendium.domain.model.Resource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    // --- renderAbilityReport -------------------------------------------------

    private fun ironEffect(description: String, cooldown: Duration = Duration.ZERO) =
        Effect.AbilityEffect(
            rank = Rank.Iron,
            type = AbilityType.Spell,
            properties = listOf(Property.Fire),
            cost = listOf(
                Cost.Upfront(
                    amount = wizardry.compendium.domain.model.Amount.Low,
                    resource = Resource.Mana,
                ),
            ),
            cooldown = cooldown,
            description = description,
            replacementKey = null,
        )

    private fun bronzeEffect(description: String) =
        Effect.AbilityEffect(
            rank = Rank.Bronze,
            type = AbilityType.Spell,
            properties = listOf(Property.Fire),
            cost = listOf(
                Cost.Upfront(
                    amount = wizardry.compendium.domain.model.Amount.Moderate,
                    resource = Resource.Mana,
                ),
            ),
            cooldown = Duration.ZERO,
            description = description,
            replacementKey = null,
        )

    @Test
    fun `renderAbilityReport header includes name and Cost Cooldown lines`() {
        val ability = Ability.Listing(
            name = "Fireball",
            effects = listOf(ironEffect("Burn the target.")),
        )

        val text = AbilityTextRenderer.renderAbilityReport(ability)

        assertTrue("missing name header", text.startsWith("Ability: Fireball"))
        assertTrue("missing Cost line", text.contains("\nCost: "))
        assertTrue("missing Cooldown line", text.contains("\nCooldown: "))
        assertTrue("missing Iron effect line", text.contains("Effect (Iron): "))
        assertTrue("missing effect description", text.contains("Burn the target."))
    }

    @Test
    fun `renderAbilityReport with rankCeiling hides higher-rank effects`() {
        val ability = Ability.Listing(
            name = "Fireball",
            effects = listOf(
                ironEffect("Initial burn."),
                bronzeEffect("Stronger burn."),
            ),
        )

        val text = AbilityTextRenderer.renderAbilityReport(ability, rankCeiling = Rank.Iron)

        assertTrue("Iron effect should be present", text.contains("Effect (Iron): "))
        assertTrue("Iron description should appear", text.contains("Initial burn."))
        assertTrue("Bronze effect should be filtered out", !text.contains("Effect (Bronze):"))
        assertTrue("Bronze description should not leak through", !text.contains("Stronger burn."))
    }

    @Test
    fun `renderAbilityReport collapses cooldown to Varies when effects disagree`() {
        val ability = Ability.Listing(
            name = "Mixed",
            effects = listOf(
                ironEffect("Zero-cooldown effect."),
                bronzeEffect("Different-cooldown effect.").copy(cooldown = 5.seconds),
            ),
        )

        val text = AbilityTextRenderer.renderAbilityReport(ability)

        assertTrue("expected Cooldown: Varies in $text", text.contains("Cooldown: Varies."))
    }

    @Test
    fun `renderAbilityReport on an empty ability still produces header and Varies fallbacks`() {
        val ability = Ability.Listing(name = "Empty", effects = emptyList())

        val text = AbilityTextRenderer.renderAbilityReport(ability)

        assertTrue("missing name header", text.startsWith("Ability: Empty"))
        // With no effects, the "type" and "properties" join to empty strings, and
        // Cost/Cooldown fall back to "Varies". Assert the structural lines are present.
        assertTrue("missing Cost: Varies fallback", text.contains("Cost: Varies."))
        assertTrue("missing Cooldown: Varies fallback", text.contains("Cooldown: Varies."))
    }
}
