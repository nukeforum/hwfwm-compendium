package wizardry.compendium.essences.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class EffectsViewTest {

    private fun effect(
        rank: Rank,
        description: String,
        replacementKey: String? = null,
    ) = Effect.AbilityEffect(
        rank = rank,
        type = AbilityType.Conjuration,
        properties = emptyList(),
        cost = emptyList(),
        cooldown = 0.seconds,
        description = description,
        replacementKey = replacementKey,
    )

    @Test
    fun `null ceiling and no replacement keys returns effects at their own ranks`() {
        val iron1 = effect(Rank.Iron, "iron-1")
        val iron2 = effect(Rank.Iron, "iron-2")
        val silver = effect(Rank.Silver, "silver")

        val view = listOf(iron1, iron2, silver).viewAt(ceiling = null)

        assertEquals(
            listOf(
                RankedEffectLine(Rank.Iron, listOf(iron1, iron2)),
                RankedEffectLine(Rank.Silver, listOf(silver)),
            ),
            view,
        )
    }

    @Test
    fun `ceiling = Iron drops Silver and higher`() {
        val iron = effect(Rank.Iron, "iron")
        val silver = effect(Rank.Silver, "silver")
        val gold = effect(Rank.Gold, "gold")

        val view = listOf(iron, silver, gold).viewAt(ceiling = Rank.Iron)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(iron))),
            view,
        )
    }

    @Test
    fun `replacement-key group collapses to highest rank effect at lowest rank slot`() {
        val ironCloak = effect(Rank.Iron, "iron-cloak", replacementKey = "cloak")
        val ironWeight = effect(Rank.Iron, "iron-weight", replacementKey = "weight")
        val silverCloak = effect(Rank.Silver, "silver-cloak", replacementKey = "cloak")
        val silverFireballs = effect(Rank.Silver, "silver-fireballs")

        val view = listOf(ironCloak, ironWeight, silverCloak, silverFireballs)
            .viewAt(ceiling = null)

        assertEquals(
            listOf(
                // Iron line: silver-cloak replaces iron-cloak at iron-cloak's slot,
                // iron-weight stays.
                RankedEffectLine(Rank.Iron, listOf(silverCloak, ironWeight)),
                // Silver line: only the un-keyed silver-fireballs remains;
                // silver-cloak was consumed into the Iron line.
                RankedEffectLine(Rank.Silver, listOf(silverFireballs)),
            ),
            view,
        )
    }
}
