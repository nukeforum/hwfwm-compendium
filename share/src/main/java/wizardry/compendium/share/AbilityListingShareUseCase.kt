package wizardry.compendium.share

import wizardry.compendium.essences.model.Ability
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.repo.WireIoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AbilityListingShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
) {
    open fun encode(listing: Ability.Listing): String = wireIo.encodeSingle(listing)

    open fun decodeSingleAbility(text: String): DecodedSingle<Ability.Listing> = decodeSingle(text) { envelope ->
        val others = envelope.manifestations.size + envelope.confluences.size +
            envelope.stones.size + envelope.statusEffects.size
        when {
            envelope.listings.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one ability. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.listings.single()))
        }
    }

    private inline fun <T> decodeSingle(
        text: String,
        extract: (Envelope) -> DecodedSingle<T>,
    ): DecodedSingle<T> = when (val r = wireIo.decodeEnvelopeOrFailed(text)) {
        is WireIoRepository.DecodeResult.Failed -> DecodedSingle.Failed(r.reason)
        is WireIoRepository.DecodeResult.Decoded -> extract(r.envelope)
    }
}
