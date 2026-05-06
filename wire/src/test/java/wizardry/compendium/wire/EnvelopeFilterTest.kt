package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeFilterTest {

    private fun fullEnvelope() = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        manifestations = listOf(Manifestation(name = "Wind", rankIndex = 1, rarityIndex = 0)),
        confluences = listOf(
            Confluence(
                name = "Doom",
                combinations = listOf(ConfluenceSet("Sin", "Blood", "Dark", 0)),
            ),
        ),
        stones = listOf(Stone(name = "Volcano", rarityIndex = 3)),
        listings = listOf(
            Listing(
                name = "Frost Bolt",
                effects = listOf(Effect(rankIndex = 1, typeIndex = 3, description = "...")),
            ),
        ),
        statusEffects = listOf(StatusEffect(name = "Bleed", typeIndex = 0)),
    )

    @Test
    fun `filteredTo empty selection zeroes all domain lists but preserves version`() {
        val filtered = fullEnvelope().filteredTo(emptySet())

        assertEquals(EnvelopeCodec.CurrentVersion, filtered.version)
        assertEquals(emptyList<Manifestation>(), filtered.manifestations)
        assertEquals(emptyList<Confluence>(), filtered.confluences)
        assertEquals(emptyList<Stone>(), filtered.stones)
        assertEquals(emptyList<Listing>(), filtered.listings)
        assertEquals(emptyList<StatusEffect>(), filtered.statusEffects)
    }

    @Test
    fun `filteredTo single domain retains only that domain`() {
        val filtered = fullEnvelope().filteredTo(setOf(ContributionDomain.AwakeningStones))

        assertEquals(emptyList<Manifestation>(), filtered.manifestations)
        assertEquals(emptyList<Confluence>(), filtered.confluences)
        assertEquals(1, filtered.stones.size)
        assertEquals("Volcano", filtered.stones.single().name)
        assertEquals(emptyList<Listing>(), filtered.listings)
        assertEquals(emptyList<StatusEffect>(), filtered.statusEffects)
    }

    @Test
    fun `filteredTo all domains is identity`() {
        val original = fullEnvelope()
        val filtered = original.filteredTo(ContributionDomain.entries.toSet())

        assertEquals(original, filtered)
    }

    @Test
    fun `filteredTo with two domains retains both lists`() {
        val filtered = fullEnvelope().filteredTo(
            setOf(ContributionDomain.Essences, ContributionDomain.StatusEffects),
        )

        assertEquals(1, filtered.manifestations.size)
        assertEquals(emptyList<Confluence>(), filtered.confluences)
        assertEquals(emptyList<Stone>(), filtered.stones)
        assertEquals(emptyList<Listing>(), filtered.listings)
        assertEquals(1, filtered.statusEffects.size)
    }
}
