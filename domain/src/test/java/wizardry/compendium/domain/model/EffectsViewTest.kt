package wizardry.compendium.domain.model

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

    @Test
    fun `replacement-key group whose lowest rank is above ceiling is hidden`() {
        val silverA = effect(Rank.Silver, "silver-a", replacementKey = "shared")
        val goldA = effect(Rank.Gold, "gold-a", replacementKey = "shared")

        val view = listOf(silverA, goldA).viewAt(ceiling = Rank.Iron)

        assertEquals(emptyList<RankedEffectLine>(), view)
    }

    @Test
    fun `replacement-key winner respects ceiling — lower-rank winner picked when higher is filtered out`() {
        val ironA = effect(Rank.Iron, "iron-a", replacementKey = "shared")
        val silverA = effect(Rank.Silver, "silver-a", replacementKey = "shared")
        val goldA = effect(Rank.Gold, "gold-a", replacementKey = "shared")

        val view = listOf(ironA, silverA, goldA).viewAt(ceiling = Rank.Silver)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(silverA))),
            view,
        )
    }

    @Test
    fun `contributor-authored order preserved within a rank line`() {
        val a = effect(Rank.Iron, "first")
        val b = effect(Rank.Iron, "second")
        val c = effect(Rank.Iron, "third")

        val view = listOf(c, a, b).viewAt(ceiling = null)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(c, a, b))),
            view,
        )
    }

    @Test
    fun `blank replacementKey does not group`() {
        val a = effect(Rank.Iron, "iron-a", replacementKey = "")
        val b = effect(Rank.Iron, "iron-b", replacementKey = "")

        val view = listOf(a, b).viewAt(ceiling = null)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(a, b))),
            view,
        )
    }

    @Test
    fun `winner inherits its own cost cooldown and properties — not the lower effect's`() {
        val ironCloak = Effect.AbilityEffect(
            rank = Rank.Iron,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Light),
            cost = listOf(Cost.Upfront(Amount.Low, Resource.Mana)),
            cooldown = 5.seconds,
            description = "iron",
            replacementKey = "cloak",
        )
        val silverCloak = Effect.AbilityEffect(
            rank = Rank.Silver,
            type = AbilityType.Conjuration,
            properties = listOf(Property.Light, Property.Darkness),
            cost = listOf(Cost.Upfront(Amount.High, Resource.Mana)),
            cooldown = 30.seconds,
            description = "silver",
            replacementKey = "cloak",
        )

        val view = listOf(ironCloak, silverCloak).viewAt(ceiling = null)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(silverCloak))),
            view,
        )
        val emitted = view.single().effects.single()
        assertEquals(30.seconds, emitted.cooldown)
        assertEquals(listOf(Property.Light, Property.Darkness), emitted.properties)
    }

    @Test
    fun `slot rank uses lowest rank in group even when higher-rank member is declared first`() {
        val goldCloak = effect(Rank.Gold, "gold-cloak", replacementKey = "cloak")
        val ironCloak = effect(Rank.Iron, "iron-cloak", replacementKey = "cloak")

        val view = listOf(goldCloak, ironCloak).viewAt(ceiling = null)

        assertEquals(
            listOf(RankedEffectLine(Rank.Iron, listOf(goldCloak))),
            view,
        )
    }

    @Test
    fun `flatMap of viewAt is the visible effect set used by summary lines and linked statuses`() {
        val ironCloak = effect(Rank.Iron, "iron-cloak", replacementKey = "cloak")
        val silverCloak = effect(Rank.Silver, "silver-cloak", replacementKey = "cloak")
        val goldFlair = effect(Rank.Gold, "gold-flair")

        // Iron ceiling: only the Iron-cloak effect (winner-within-ceiling) is visible.
        val ironVisible = listOf(ironCloak, silverCloak, goldFlair)
            .viewAt(Rank.Iron)
            .flatMap { it.effects }
        assertEquals(listOf(ironCloak), ironVisible)

        // Silver ceiling: silver-cloak (winner) supersedes iron-cloak; gold-flair filtered out.
        val silverVisible = listOf(ironCloak, silverCloak, goldFlair)
            .viewAt(Rank.Silver)
            .flatMap { it.effects }
        assertEquals(listOf(silverCloak), silverVisible)

        // Null ceiling: silverCloak winner + goldFlair.
        val fullVisible = listOf(ironCloak, silverCloak, goldFlair)
            .viewAt(ceiling = null)
            .flatMap { it.effects }
        assertEquals(listOf(silverCloak, goldFlair), fullVisible)
    }

    @Test
    fun `ungrouped effect between two grouped members renders in declaration order on the same line`() {
        val ironCloak = effect(Rank.Iron, "iron-cloak", replacementKey = "cloak")
        val ironWeight = effect(Rank.Iron, "iron-weight")
        val silverCloak = effect(Rank.Silver, "silver-cloak", replacementKey = "cloak")

        val view = listOf(ironCloak, ironWeight, silverCloak).viewAt(ceiling = null)

        assertEquals(
            listOf(
                // Iron line: winner-of-cloak (silverCloak) fires at ironCloak's position, then ironWeight.
                RankedEffectLine(Rank.Iron, listOf(silverCloak, ironWeight)),
            ),
            view,
        )
    }
}
