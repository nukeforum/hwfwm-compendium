package wizardry.compendium.wire

import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Essence

/**
 * Builds wire envelopes from the user's local contributions.
 *
 * # What `exportAll` returns
 *
 * Every contribution the user has — essence manifestations and confluences,
 * awakening stones, ability listings — wrapped in a single Envelope at the
 * current wire-format version.
 *
 * # Why include zero-entry domains
 *
 * If the user has no awakening-stone contributions, the envelope still has
 * a (default-empty) `stones` list rather than skipping the field. The codec
 * omits empty defaults on the wire, so this costs nothing in size and keeps
 * the data shape uniform for the importer.
 *
 * # Per-table exports come later
 *
 * Tier 4 starts with a single "export everything" path. Per-table variants
 * (just essences, just stones, etc.) are easy to add as a filter step on
 * top of `exportAll` and will land alongside the per-table UI.
 */
class WireExporter(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
) {

    suspend fun exportAll(): Envelope {
        val essences = essenceRepository.getContributions()
        val manifestations = essences
            .filterIsInstance<Essence.Manifestation>()
            .map { EnvelopeMapper.toWire(it) }
        val confluences = essences
            .filterIsInstance<Essence.Confluence>()
            .map { EnvelopeMapper.toWire(it) }
        val stones = awakeningStoneRepository.getContributions().map { EnvelopeMapper.toWire(it) }
        val listings = abilityListingRepository.getContributions().map { EnvelopeMapper.toWire(it) }
        return Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = manifestations,
            confluences = confluences,
            stones = stones,
            listings = listings,
        )
    }
}
