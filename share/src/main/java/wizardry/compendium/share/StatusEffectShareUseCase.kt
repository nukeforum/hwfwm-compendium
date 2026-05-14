package wizardry.compendium.share

import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.repo.WireIoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class StatusEffectShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
) {
    open fun encode(effect: StatusEffect): String = wireIo.encodeSingle(effect)

    open fun decodeSingleStatusEffect(text: String): DecodedSingle<StatusEffect> = decodeSingle(text) { envelope ->
        val others = envelope.manifestations.size + envelope.confluences.size +
            envelope.stones.size + envelope.listings.size
        when {
            envelope.statusEffects.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one status effect. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.statusEffects.single()))
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
