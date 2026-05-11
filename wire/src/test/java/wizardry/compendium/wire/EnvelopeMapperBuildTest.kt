package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity

class EnvelopeMapperBuildTest {

    private val wind = manifestation("Wind")
    private val frostBoltListing = listing("Frost Bolt")

    @Test
    fun `empty build round-trips`() {
        val build = CharacterBuild(name = "Empty", race = "Human", racialAbilities = emptyList())
        val wire = EnvelopeMapper.toWire(build)
        assertEquals("Empty", wire.name)
        assertEquals("Human", wire.race)
        assertTrue(wire.racials.isEmpty())
        assertEquals(4, wire.attributes.size)
        assertTrue(wire.attributes.all { it.essenceName.isEmpty() && it.abilities.isEmpty() })

        val decoded = EnvelopeMapper.toModel(
            wire = wire,
            manifestationLookup = { null },
            confluenceLookup = { null },
            listingLookup = { null },
        )
        assertEquals("Empty", decoded.name)
        assertEquals("Human", decoded.race)
        assertNull(decoded.Power.essence)
        assertNull(decoded.Speed.essence)
        assertNull(decoded.Spirit.essence)
        assertNull(decoded.Recovery.essence)
        assertTrue(decoded.racialAbilities.isEmpty())
    }

    @Test
    fun `manifestation slot with abilities round-trips`() {
        val build = CharacterBuild(
            name = "Frosty",
            race = "Human",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(
                    essence = AbsorbedEssence(
                        essence = wind,
                        abilities = listOf(
                            frostBoltListing.acquire(wind).copy(rank = Rank.Bronze, tier = 2, progress = 0.5f),
                        ),
                    ),
                ),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val wire = EnvelopeMapper.toWire(build)
        assertEquals("Wind", wire.attributes[0].essenceName)
        assertEquals(false, wire.attributes[0].confluence)
        assertEquals(1, wire.attributes[0].abilities.size)
        assertEquals(
            BuildAbility(name = "Frost Bolt", rankIndex = Rank.Bronze.ordinal, tier = 2, progress = 0.5f),
            wire.attributes[0].abilities[0],
        )

        val decoded = EnvelopeMapper.toModel(
            wire = wire,
            manifestationLookup = { if (it == "Wind") wind else null },
            confluenceLookup = { null },
            listingLookup = { if (it == "Frost Bolt") frostBoltListing else null },
        )
        val powerEssence = decoded.Power.essence!!
        assertSame(wind, powerEssence.essence)
        assertEquals(1, powerEssence.abilities.size)
        val ability = powerEssence.abilities.single()
        assertEquals("Frost Bolt", ability.name)
        assertEquals(Rank.Bronze, ability.rank)
        assertEquals(2, ability.tier)
        assertEquals(0.5f, ability.progress, 0.0f)
    }

    @Test
    fun `missing essence drops slot on decode`() {
        val wire = Build(
            name = "Test", race = "Human",
            attributes = listOf(
                BuildAttribute(essenceName = "Unknown"),
                BuildAttribute(),
                BuildAttribute(),
                BuildAttribute(),
            ),
        )
        val decoded = EnvelopeMapper.toModel(
            wire = wire,
            manifestationLookup = { null },
            confluenceLookup = { null },
            listingLookup = { null },
        )
        assertNull(decoded.Power.essence)
    }

    @Test
    fun `missing ability listing drops ability from slot`() {
        val wire = Build(
            name = "Test", race = "Human",
            attributes = listOf(
                BuildAttribute(
                    essenceName = "Wind",
                    abilities = listOf(BuildAbility(name = "Unknown", rankIndex = 1, tier = 0, progress = 0f)),
                ),
                BuildAttribute(), BuildAttribute(), BuildAttribute(),
            ),
        )
        val decoded = EnvelopeMapper.toModel(
            wire = wire,
            manifestationLookup = { if (it == "Wind") wind else null },
            confluenceLookup = { null },
            listingLookup = { null },
        )
        assertSame(wind, decoded.Power.essence!!.essence)
        assertTrue(decoded.Power.essence!!.abilities.isEmpty())
    }

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name, rank = Rank.Iron, rarity = Rarity.Common,
        properties = emptyList(), description = "", isRestricted = false,
    )

    private fun listing(name: String) = Ability.Listing(name = name, effects = emptyList())
}
