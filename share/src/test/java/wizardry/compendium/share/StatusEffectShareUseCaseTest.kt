package wizardry.compendium.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper

class StatusEffectShareUseCaseTest {

    private val burn = StatusEffect(
        name = "Burn",
        type = StatusType.Affliction.Elemental,
        properties = listOf(Property.DamageOverTime, Property.Fire),
        stackable = true,
        description = "Deals fire damage over time.",
    )

    private fun newUseCase() = StatusEffectShareUseCase(wireIo = stubWireIoRepository())

    @Test
    fun `encode round-trips through decode for a single status effect`() {
        val useCase = newUseCase()
        val text = useCase.encode(burn)
        val result = useCase.decodeSingleStatusEffect(text)
        assertTrue(result is DecodedSingle.Loaded)
        assertEquals(burn, (result as DecodedSingle.Loaded).model)
    }

    @Test
    fun `decode rejects envelope containing a status effect plus an unrelated entry`() {
        val useCase = newUseCase()
        val stone = AwakeningStone.of("Volcano", Rarity.Epic)
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            statusEffects = listOf(EnvelopeMapper.toWire(burn)),
            stones = listOf(EnvelopeMapper.toWire(stone)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = useCase.decodeSingleStatusEffect(text)
        assertEquals(
            "This share doesn't contain exactly one status effect. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `decode rejects empty paste`() {
        val result = newUseCase().decodeSingleStatusEffect("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }

    @Test
    fun `renderAsText includes name type description and properties`() {
        val text = newUseCase().renderAsText(burn)
        assertTrue("missing name in $text", text.contains("Burn"))
        assertTrue("missing type label in $text", text.contains("Affliction"))
        assertTrue("missing description in $text", text.contains("Deals fire damage over time."))
        assertTrue("missing properties in $text", text.contains("fire"))
        assertTrue("missing stackable marker in $text", text.contains("Stackable"))
    }
}
