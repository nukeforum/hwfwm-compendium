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

class AwakeningStoneShareUseCaseTest {

    private val volcano = AwakeningStone.of("Volcano", Rarity.Epic)

    private fun newUseCase() = AwakeningStoneShareUseCase(wireIo = stubWireIoRepository())

    @Test
    fun `encode round-trips through decode for a single stone`() {
        val useCase = newUseCase()
        val text = useCase.encode(volcano)
        val result = useCase.decodeSingleStone(text)
        assertTrue(result is DecodedSingle.Loaded)
        assertEquals(volcano, (result as DecodedSingle.Loaded).model)
    }

    @Test
    fun `decode rejects envelope with stone and unrelated entry`() {
        val useCase = newUseCase()
        val burn = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire),
            stackable = true,
            description = "fire dot",
        )
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            stones = listOf(EnvelopeMapper.toWire(volcano)),
            statusEffects = listOf(EnvelopeMapper.toWire(burn)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = useCase.decodeSingleStone(text)
        assertEquals(
            "This share doesn't contain exactly one awakening stone. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `decode rejects empty paste`() {
        val result = newUseCase().decodeSingleStone("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }
}
