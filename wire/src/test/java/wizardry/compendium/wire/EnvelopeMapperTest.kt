package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet as ModelConfluenceSet
import wizardry.compendium.essences.model.Cost as ModelCost
import wizardry.compendium.essences.model.Effect as ModelEffect
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.Resource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class EnvelopeMapperTest {

    @Test
    fun `manifestation round trips`() {
        val original = Essence.Manifestation(
            name = "Wind",
            rank = Rank.Iron,
            rarity = Rarity.Common,
            properties = listOf(Property.Magic, Property.Movement),
            description = "A flowing breath of air.",
            isRestricted = false,
        )
        val wire = EnvelopeMapper.toWire(original)
        val decoded = EnvelopeMapper.toModel(wire)
        // Note: model Essence.Manifestation includes derived `effects`; equality
        // uses data class semantics on the constructor params, which match.
        assertEquals(original.name, decoded.name)
        assertEquals(original.rank, decoded.rank)
        assertEquals(original.rarity, decoded.rarity)
        assertEquals(original.properties, decoded.properties)
        assertEquals(original.description, decoded.description)
        assertEquals(original.isRestricted, decoded.isRestricted)
    }

    @Test
    fun `confluence round trips with manifestation lookup`() {
        val wind = manifestation("Wind")
        val rain = manifestation("Rain")
        val storm = manifestation("Storm")
        val original = Essence.Confluence(
            name = "Tempest",
            confluenceSets = setOf(
                ModelConfluenceSet(wind, rain, storm, restricted = false),
            ),
            isRestricted = false,
        )
        val wire = EnvelopeMapper.toWire(original)
        val lookup = mapOf("Wind" to wind, "Rain" to rain, "Storm" to storm)
        val decoded = EnvelopeMapper.toModel(wire) { lookup[it] }
        assertEquals(original.name, decoded.name)
        assertEquals(original.isRestricted, decoded.isRestricted)
        assertEquals(1, decoded.confluenceSets.size)
        val decodedSet = decoded.confluenceSets.single()
        assertEquals(setOf("Wind", "Rain", "Storm"), decodedSet.set.map { it.name }.toSet())
    }

    @Test
    fun `confluence with unresolved manifestation reference fails decode`() {
        val wind = manifestation("Wind")
        val rain = manifestation("Rain")
        val storm = manifestation("Storm")
        val original = Essence.Confluence(
            name = "Tempest",
            confluenceSets = setOf(
                ModelConfluenceSet(wind, rain, storm, restricted = false),
            ),
            isRestricted = false,
        )
        val wire = EnvelopeMapper.toWire(original)
        // Lookup misses Storm — decode should fail.
        val partial = mapOf("Wind" to wind, "Rain" to rain)
        try {
            EnvelopeMapper.toModel(wire) { partial[it] }
            fail("Expected WireDecodeException for unresolved manifestation")
        } catch (e: WireDecodeException) {
            assertTrue(
                "error mentions the missing name",
                e.message!!.contains("Storm"),
            )
        }
    }

    @Test
    fun `restricted flag round trips on confluence sets`() {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val original = Essence.Confluence(
            name = "Doom",
            confluenceSets = setOf(
                ModelConfluenceSet(a, b, c, restricted = true),
            ),
            isRestricted = false,
        )
        val wire = EnvelopeMapper.toWire(original)
        assertEquals(1, wire.combinations.single().restrictedFlag)
        val lookup = mapOf("A" to a, "B" to b, "C" to c)
        val decoded = EnvelopeMapper.toModel(wire) { lookup[it] }
        assertTrue(decoded.confluenceSets.single().isRestricted)
    }

    @Test
    fun `awakening stone round trips`() {
        val original = AwakeningStone.of("Volcano", Rarity.Epic)
        val wire = EnvelopeMapper.toWire(original)
        val decoded = EnvelopeMapper.toModel(wire)
        assertEquals(original.name, decoded.name)
        assertEquals(original.rarity, decoded.rarity)
    }

    @Test
    fun `ability listing with effects and costs round trips`() {
        val original = Ability.Listing(
            name = "Frost Bolt",
            effects = listOf(
                ModelEffect.AbilityEffect(
                    rank = Rank.Iron,
                    type = AbilityType.Spell,
                    properties = listOf(Property.Ice, Property.Magic),
                    cost = listOf(ModelCost.Upfront(Amount.Low, Resource.Mana)),
                    cooldown = 5.seconds,
                    description = "Hurls a bolt of frost.",
                    replacementKey = null,
                ),
            ),
        )
        val wire = EnvelopeMapper.toWire(original)
        val decoded = EnvelopeMapper.toModel(wire)
        assertEquals(original.name, decoded.name)
        assertEquals(original.effects.size, decoded.effects.size)
        val origEffect = original.effects.first()
        val decEffect = decoded.effects.first()
        assertEquals(origEffect.rank, decEffect.rank)
        assertEquals(origEffect.type, decEffect.type)
        assertEquals(origEffect.properties, decEffect.properties)
        assertEquals(origEffect.cost, decEffect.cost)
        assertEquals(origEffect.cooldown, decEffect.cooldown)
        assertEquals(origEffect.description, decEffect.description)
        assertNull(decEffect.replacementKey)
    }

    @Test
    fun `effect with no costs decodes as Cost None sentinel`() {
        val original = Ability.Listing(
            name = "Aim",
            effects = listOf(
                ModelEffect.AbilityEffect(
                    rank = Rank.Bronze,
                    type = AbilityType.SpecialAttack,
                    properties = listOf(Property.Movement),
                    cost = listOf(ModelCost.None),
                    cooldown = Duration.ZERO,
                    description = "Take aim.",
                ),
            ),
        )
        val wire = EnvelopeMapper.toWire(original)
        // Wire form omits Cost.None entirely.
        assertTrue(wire.effects.single().costs.isEmpty())
        val decoded = EnvelopeMapper.toModel(wire)
        // Receiver reconstructs Cost.None as the sentinel.
        assertEquals(listOf(ModelCost.None), decoded.effects.single().cost)
    }

    @Test
    fun `effect with ongoing cost preserves kind`() {
        val original = Ability.Listing(
            name = "Aura",
            effects = listOf(
                ModelEffect.AbilityEffect(
                    rank = Rank.Iron,
                    type = AbilityType.Aura,
                    properties = listOf(Property.Magic),
                    cost = listOf(ModelCost.Ongoing(Amount.Moderate, Resource.Mana)),
                    cooldown = Duration.ZERO,
                    description = "Sustained energy field.",
                ),
            ),
        )
        val wire = EnvelopeMapper.toWire(original)
        assertEquals("O", wire.effects.single().costs.single().kind)
        val decoded = EnvelopeMapper.toModel(wire)
        assertTrue(decoded.effects.single().cost.single() is ModelCost.Ongoing)
    }

    @Test
    fun `out-of-range enum index produces WireDecodeException`() {
        val wire = Manifestation(
            name = "Bogus",
            rankIndex = 999,
            rarityIndex = 0,
        )
        try {
            EnvelopeMapper.toModel(wire)
            fail("Expected WireDecodeException for out-of-range rank index")
        } catch (e: WireDecodeException) {
            assertTrue(e.message!!.contains("Rank") || e.message!!.contains("range"))
        }
    }
}

private fun manifestation(name: String): Essence.Manifestation = Essence.Manifestation(
    name = name,
    rank = Rank.Iron,
    rarity = Rarity.Common,
    properties = emptyList(),
    description = "",
    isRestricted = false,
)
