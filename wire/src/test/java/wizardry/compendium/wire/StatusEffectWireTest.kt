package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectWireTest {

    @Test
    fun `single status-effect round-trips through codec`() {
        val sample = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.DamageOverTime, Property.Fire),
            stackable = true,
            description = "Deals fire damage over time.",
        )
        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            statusEffects = listOf(EnvelopeMapper.toWire(sample)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val decoded = EnvelopeCodec.decode(text)

        assertEquals(1, decoded.statusEffects.size)
        assertEquals(sample, EnvelopeMapper.toModel(decoded.statusEffects.single()))
    }

    @Test
    fun `empty properties list round-trips`() {
        val sample = StatusEffect(
            name = "Recovery",
            type = StatusType.Boon.UnTyped,
            properties = emptyList(),
            stackable = false,
            description = "",
        )
        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            statusEffects = listOf(EnvelopeMapper.toWire(sample)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val decoded = EnvelopeCodec.decode(text)

        assertEquals(sample, EnvelopeMapper.toModel(decoded.statusEffects.single()))
    }

    @Test
    fun `envelope without statusEffects decodes with empty list`() {
        val envelope = Envelope(version = EnvelopeCodec.CurrentVersion)
        val text = EnvelopeCodec.encode(envelope).text
        val decoded = EnvelopeCodec.decode(text)
        assertTrue(decoded.statusEffects.isEmpty())
    }

    @Test
    fun `boon-unholy round-trips through index 11`() {
        val sample = StatusEffect(
            name = "Sanctify",
            type = StatusType.Boon.Unholy,
            properties = emptyList(),
            stackable = true,
            description = "",
        )
        val wire = EnvelopeMapper.toWire(sample)
        // Pin the index — Boon.Unholy is at slot 11 of the table
        // (Affliction.* take slots 0-8, Boon.Holy=9, Boon.Magic=10, Boon.Unholy=11)
        assertEquals(11, wire.typeIndex)
        assertEquals(sample, EnvelopeMapper.toModel(wire))
    }
}
