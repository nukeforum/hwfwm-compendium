package wizardry.compendium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration

class CostSummaryTest {

    private val mana = Resource.Mana
    private val stamina = Resource.Stamina

    private fun effect(
        rank: Rank = Rank.Iron,
        costs: List<Cost>,
        replacementKey: String? = null,
    ): Effect.AbilityEffect = Effect.AbilityEffect(
        rank = rank,
        type = AbilityType.Spell,
        properties = emptyList(),
        cost = costs,
        cooldown = Duration.ZERO,
        description = "x",
        replacementKey = replacementKey,
    )

    @Test
    fun `Rule 1 — empty list summarizes to None`() {
        assertEquals(CostSummary.None, emptyList<Effect.AbilityEffect>().summarizeCost())
    }

    @Test
    fun `Rule 1 — all effects with Cost None summarize to None`() {
        val effects = listOf(
            effect(costs = listOf(Cost.None)),
            effect(costs = listOf(Cost.None)),
        )
        assertEquals(CostSummary.None, effects.summarizeCost())
    }

    @Test
    fun `Rule 2 — one effect with one non-None cost summarizes to Single`() {
        val cost = Cost.Upfront(Amount.Low, mana)
        val effects = listOf(effect(costs = listOf(cost)))
        assertEquals(CostSummary.Single(cost), effects.summarizeCost())
    }

    @Test
    fun `Rule 4 — multiple effects with identical costs summarize to Single`() {
        val cost = Cost.Upfront(Amount.Moderate, mana)
        val effects = listOf(
            effect(costs = listOf(cost)),
            effect(costs = listOf(cost)),
            effect(costs = listOf(cost)),
        )
        assertEquals(CostSummary.Single(cost), effects.summarizeCost())
    }

    @Test
    fun `Rule 5 — differing costs summarize to Varies`() {
        val effects = listOf(
            effect(costs = listOf(Cost.Upfront(Amount.Low, mana))),
            effect(costs = listOf(Cost.Upfront(Amount.High, mana))),
        )
        assertEquals(CostSummary.Varies, effects.summarizeCost())
    }

    @Test
    fun `Rule 5 — same amount different resource summarizes to Varies`() {
        val effects = listOf(
            effect(costs = listOf(Cost.Upfront(Amount.Low, mana))),
            effect(costs = listOf(Cost.Upfront(Amount.Low, stamina))),
        )
        assertEquals(CostSummary.Varies, effects.summarizeCost())
    }

    @Test
    fun `Cost None filtered out — Single non-None cost alongside Cost None summarizes to Single`() {
        val cost = Cost.Upfront(Amount.Low, mana)
        val effects = listOf(
            effect(costs = listOf(cost)),
            effect(costs = listOf(Cost.None)),
        )
        assertEquals(CostSummary.Single(cost), effects.summarizeCost())
    }

    @Test
    fun `Rule 3 — post-viewAt input collapses replacement-key groups before summarizing`() {
        val ironCost = Cost.Upfront(Amount.Low, mana)
        val bronzeCost = Cost.Upfront(Amount.Moderate, mana)
        val effects = listOf(
            effect(rank = Rank.Iron, costs = listOf(ironCost), replacementKey = "fire"),
            effect(rank = Rank.Bronze, costs = listOf(bronzeCost), replacementKey = "fire"),
        )

        val visible = effects.viewAt(ceiling = Rank.Bronze).flatMap { it.effects }

        assertEquals(CostSummary.Single(bronzeCost), visible.summarizeCost())
    }

    @Test
    fun `render — None renders as 'None'`() {
        assertEquals("None", CostSummary.None.render())
    }

    @Test
    fun `render — Single delegates to Cost toString`() {
        val cost = Cost.Upfront(Amount.Low, mana)
        assertEquals(cost.toString(), CostSummary.Single(cost).render())
    }

    @Test
    fun `render — Varies renders as 'Varies'`() {
        assertEquals("Varies", CostSummary.Varies.render())
    }
}
