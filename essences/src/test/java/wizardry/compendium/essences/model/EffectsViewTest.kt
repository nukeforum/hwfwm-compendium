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
}
