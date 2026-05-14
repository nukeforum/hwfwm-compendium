package wizardry.compendium.share

import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.repo.WireIoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class EssenceShareUseCase @Inject constructor(
    private val wireIo: WireIoRepository,
    private val essenceRepository: EssenceRepository,
) {
    open fun encode(essence: Essence): String = wireIo.encodeSingle(essence)

    open fun decodeSingleManifestation(text: String): DecodedSingle<Essence.Manifestation> = decodeSingle(text) { envelope ->
        val others = envelope.confluences.size + envelope.stones.size +
            envelope.listings.size + envelope.statusEffects.size
        when {
            envelope.manifestations.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one essence manifestation. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.manifestations.single()))
        }
    }

    open suspend fun decodeConfluenceBundle(text: String): DecodedSingle<ConfluenceImportPreview> {
        val envelope = when (val r = wireIo.decodeEnvelopeOrFailed(text)) {
            is WireIoRepository.DecodeResult.Failed -> return DecodedSingle.Failed(r.reason)
            is WireIoRepository.DecodeResult.Decoded -> r.envelope
        }

        val others = envelope.stones.size + envelope.listings.size + envelope.statusEffects.size
        if (envelope.confluences.size != 1 || others > 0) {
            return DecodedSingle.Failed(
                "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            )
        }

        val wireConfluence = envelope.confluences.single()
        val bundledEssences = envelope.manifestations.map { EnvelopeMapper.toModel(it) }
        val bundledByLower = bundledEssences.associateBy { it.name.lowercase() }

        val dbByLower = essenceRepository.getEssences()
            .filterIsInstance<Essence.Manifestation>()
            .associateBy { it.name.lowercase() }

        val previewEssences = bundledEssences.map { e ->
            PreviewEssence(
                name = e.name,
                rarity = e.rarity,
                isNew = !dbByLower.containsKey(e.name.lowercase()),
            )
        }

        val combinations = wireConfluence.combinations.map { set ->
            PreviewCombination(
                essence1 = set.name1,
                essence2 = set.name2,
                essence3 = set.name3,
                isRestricted = set.restrictedFlag != 0,
            )
        }

        val unresolvable = combinations
            .flatMap { listOf(it.essence1, it.essence2, it.essence3) }
            .map { it.lowercase() }
            .filter { it !in bundledByLower && it !in dbByLower }
            .toSet()

        return DecodedSingle.Loaded(
            ConfluenceImportPreview(
                envelope = envelope,
                confluenceName = wireConfluence.name,
                isRestricted = wireConfluence.isRestricted,
                combinations = combinations,
                essences = previewEssences,
                unresolvableNames = unresolvable,
            ),
        )
    }

    private inline fun <T> decodeSingle(
        text: String,
        extract: (Envelope) -> DecodedSingle<T>,
    ): DecodedSingle<T> = when (val r = wireIo.decodeEnvelopeOrFailed(text)) {
        is WireIoRepository.DecodeResult.Failed -> DecodedSingle.Failed(r.reason)
        is WireIoRepository.DecodeResult.Decoded -> extract(r.envelope)
    }
}
