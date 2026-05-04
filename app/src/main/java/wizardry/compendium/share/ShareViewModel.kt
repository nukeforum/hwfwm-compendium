package wizardry.compendium.share

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.WireDecodeException
import wizardry.compendium.wire.WireExporter
import wizardry.compendium.wire.WireVersionUnsupported
import javax.inject.Inject

/**
 * Encodes a single contributed entity into a share-ready text blob.
 *
 * # Why a ViewModel rather than a top-level function
 *
 * The single-entity export functions on `WireExporter` don't actually
 * touch the repositories — but the existing exporter API takes them in
 * its constructor (for `exportAll`). Wrapping in a Hilt VM gets the repo
 * injection for free without contorting the exporter.
 *
 * # Synchronous API
 *
 * Single-entity encoding is sub-millisecond for any realistic payload, so
 * we return strings synchronously rather than via Flow / suspend. If a
 * caller ever exports something huge from a detail screen, this becomes
 * a perf concern; revisit then.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
    awakeningStoneRepository: AwakeningStoneRepository,
    abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    private val exporter = WireExporter(
        essenceRepository,
        awakeningStoneRepository,
        abilityListingRepository,
    )

    fun encode(essence: Essence): String = when (essence) {
        is Essence.Manifestation -> EnvelopeCodec.encode(exporter.exportSingle(essence)).text
        is Essence.Confluence -> EnvelopeCodec.encode(exporter.exportSingle(essence)).text
        else -> error("Unsupported Essence subtype: ${essence::class}")
    }

    fun encode(stone: AwakeningStone): String =
        EnvelopeCodec.encode(exporter.exportSingle(stone)).text

    fun encode(listing: Ability.Listing): String =
        EnvelopeCodec.encode(exporter.exportSingle(listing)).text

    /**
     * Result of decoding a paste-buffer for the Contribute-screen Import
     * action. The contribute form pre-fills from the model in `Loaded`;
     * `Failed` surfaces a user-facing error.
     */
    sealed interface DecodedSingle<out T> {
        data class Loaded<T>(val model: T) : DecodedSingle<T>
        data class Failed(val reason: String) : DecodedSingle<Nothing>
    }

    /**
     * Decode a paste-buffer and extract a single awakening stone, if the
     * envelope contains exactly one and nothing else. Anything that doesn't
     * match (multi-entity envelope, wrong domain, malformed) becomes
     * `Failed` with a user-friendly message.
     *
     * Why we reject multi-entity envelopes here even when one of the
     * entries matches: the user is on a single-contribution form. Pasting
     * a full-DB share should funnel them to Settings → Import instead,
     * not silently grab one entry. Explicit error keeps the model clean.
     */
    fun decodeSingleStone(text: String): DecodedSingle<AwakeningStone> = decodeSingle(text) { envelope ->
        val others = envelope.manifestations.size + envelope.confluences.size + envelope.listings.size
        when {
            envelope.stones.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one awakening stone. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.stones.single()))
        }
    }

    fun decodeSingleListing(text: String): DecodedSingle<Ability.Listing> = decodeSingle(text) { envelope ->
        val others = envelope.manifestations.size + envelope.confluences.size + envelope.stones.size
        when {
            envelope.listings.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one ability listing. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.listings.single()))
        }
    }

    /**
     * Decode a paste containing exactly one essence manifestation.
     *
     * Confluences are NOT accepted by this entry point — they have their
     * own contribute sub-form (the Confluence tab) and embed manifestation
     * references that the pre-fill form can't represent. Multi-entry
     * shares route to Settings → Import.
     */
    fun decodeSingleManifestation(text: String): DecodedSingle<Essence.Manifestation> = decodeSingle(text) { envelope ->
        val others = envelope.confluences.size + envelope.stones.size + envelope.listings.size
        when {
            envelope.manifestations.size != 1 || others > 0 -> DecodedSingle.Failed(
                "This share doesn't contain exactly one essence manifestation. Use Settings → Import for multi-entry shares.",
            )
            else -> DecodedSingle.Loaded(EnvelopeMapper.toModel(envelope.manifestations.single()))
        }
    }

    /**
     * Decode a paste containing exactly one confluence (with its bundled
     * essences) and build a preview for the review sheet.
     *
     * Suspends because it reads the receiver's current essences to compute
     * `isNew` per bundled essence and `unresolvableNames` for combination
     * references that aren't bundled and aren't in the DB.
     */
    suspend fun decodeConfluenceBundle(text: String): DecodedSingle<ConfluenceImportPreview> {
        val envelope = when (val r = decodeEnvelopeOrFailed(text)) {
            is EnvelopeResult.Failed -> return DecodedSingle.Failed(r.reason)
            is EnvelopeResult.Decoded -> r.envelope
        }

        val others = envelope.stones.size + envelope.listings.size
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
    ): DecodedSingle<T> = when (val r = decodeEnvelopeOrFailed(text)) {
        is EnvelopeResult.Failed -> DecodedSingle.Failed(r.reason)
        is EnvelopeResult.Decoded -> extract(r.envelope)
    }

    /**
     * Shared envelope-decoding step for all paste-buffer entry points.
     * Centralizes the empty-paste / version-unsupported / decode-failure /
     * generic-exception ladder so each consumer can focus on shape checks.
     */
    private fun decodeEnvelopeOrFailed(text: String): EnvelopeResult {
        if (text.isBlank()) return EnvelopeResult.Failed("Paste is empty.")
        return try {
            EnvelopeResult.Decoded(EnvelopeCodec.decode(text))
        } catch (e: WireVersionUnsupported) {
            EnvelopeResult.Failed("This share was made with a newer app version. Update to import.")
        } catch (e: WireDecodeException) {
            EnvelopeResult.Failed(e.message ?: "Pasted data is not a valid contribution share.")
        } catch (e: Exception) {
            EnvelopeResult.Failed("Import failed: ${e.message}")
        }
    }

    private sealed interface EnvelopeResult {
        data class Decoded(val envelope: Envelope) : EnvelopeResult
        data class Failed(val reason: String) : EnvelopeResult
    }
}
