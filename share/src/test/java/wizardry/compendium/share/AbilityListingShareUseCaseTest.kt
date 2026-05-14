package wizardry.compendium.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper

class AbilityListingShareUseCaseTest {

    private val fireball = Ability.Listing.of("Fireball")

    private fun newUseCase() = AbilityListingShareUseCase(wireIo = stubWireIoRepository())

    @Test
    fun `encode round-trips for a single listing`() {
        val useCase = newUseCase()
        val text = useCase.encode(fireball)
        val result = useCase.decodeSingleAbility(text)
        assertTrue(result is DecodedSingle.Loaded)
        assertEquals(fireball, (result as DecodedSingle.Loaded).model)
    }

    @Test
    fun `decode rejects envelope with listing and stone`() {
        val useCase = newUseCase()
        val stone = AwakeningStone.of("Volcano", Rarity.Epic)
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            listings = listOf(EnvelopeMapper.toWire(fireball)),
            stones = listOf(EnvelopeMapper.toWire(stone)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = useCase.decodeSingleAbility(text)
        assertEquals(
            "This share doesn't contain exactly one ability. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `decode rejects empty paste`() {
        val result = newUseCase().decodeSingleAbility("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }
}
