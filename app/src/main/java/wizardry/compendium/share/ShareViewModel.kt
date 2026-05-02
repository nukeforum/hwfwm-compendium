package wizardry.compendium.share

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Essence
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
    essenceRepository: EssenceRepository,
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

    private inline fun <T> decodeSingle(
        text: String,
        extract: (wizardry.compendium.wire.Envelope) -> DecodedSingle<T>,
    ): DecodedSingle<T> {
        if (text.isBlank()) return DecodedSingle.Failed("Paste is empty.")
        return try {
            extract(EnvelopeCodec.decode(text))
        } catch (e: WireVersionUnsupported) {
            DecodedSingle.Failed("This share was made with a newer app version. Update to import.")
        } catch (e: WireDecodeException) {
            DecodedSingle.Failed(e.message ?: "Pasted data is not a valid contribution share.")
        } catch (e: Exception) {
            DecodedSingle.Failed("Import failed: ${e.message}")
        }
    }
}
