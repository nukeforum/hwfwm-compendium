package wizardry.compendium.share

import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.repo.WireIoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AwakeningStoneShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
) {
    open fun encode(stone: AwakeningStone): String = wireIo.encodeSingle(stone)

    open fun renderAsText(stone: AwakeningStone): String = """
        Item: [${stone.name} Awakening Stone]
        (${stone.rank.toString().lowercase()}, ${stone.rarity.toString().lowercase()})

        ${stone.description} (${stone.properties.joinToString(", ")}).

        ${stone.effects.joinToString { "Effect: ${it.description}" }}
    """.trimIndent()

    open fun decodeSingleStone(text: String): DecodedSingle<AwakeningStone> = decodeSingle(text) { envelope ->
        val others = envelope.manifestations.size + envelope.confluences.size +
            envelope.listings.size + envelope.statusEffects.size
        when {
            envelope.stones.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one awakening stone. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.stones.single()))
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
