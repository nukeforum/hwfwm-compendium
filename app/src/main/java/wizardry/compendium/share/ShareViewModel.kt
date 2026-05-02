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
import wizardry.compendium.wire.WireExporter
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
}
