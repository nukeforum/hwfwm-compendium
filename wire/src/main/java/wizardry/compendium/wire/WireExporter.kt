package wizardry.compendium.wire

import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.StatusEffect as ModelStatusEffect

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
 * # Per-table exports
 *
 * `exportFiltered` trims the result of `exportAll` to a caller-supplied
 * subset of `ContributionDomain`s, used by the Settings export picker.
 */
class WireExporter(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
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
        val effects = statusEffectRepository.getContributions().map { EnvelopeMapper.toWire(it) }
        return Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = manifestations,
            confluences = confluences,
            stones = stones,
            listings = listings,
            statusEffects = effects,
        )
    }

    /**
     * Like `exportAll`, but trims the resulting envelope to only the domains
     * named in [selection]. Used by the Settings export picker to honor the
     * user's per-domain checkbox state.
     */
    suspend fun exportFiltered(selection: Set<ContributionDomain>): Envelope =
        exportAll().filteredTo(selection)

    /**
     * Wraps a single manifestation in an envelope.
     *
     * Used by the detail-screen "Share" action. The receiver imports the
     * single entry; if they already have a same-name entity, the importer
     * returns SkippedDuplicate and they're informed cleanly.
     */
    fun exportSingle(manifestation: Essence.Manifestation): Envelope = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        manifestations = listOf(EnvelopeMapper.toWire(manifestation)),
    )

    /**
     * Wraps a single confluence in an envelope. The referenced
     * manifestations are included alongside so the receiver can resolve
     * the combination's name references even if some are user
     * contributions the receiver hasn't seen yet.
     *
     * # Why we include all referenced manifestations rather than just the
     * # ones we know are user contributions
     *
     * The exporter doesn't have a cheap way to tell which manifestations
     * are user contributions vs. canonical without an extra `isContribution`
     * call per manifestation. Including all is a few extra bytes (typically
     * 3 mfns per combination, each ~30 chars on the wire) in exchange for
     * straightforward code; the receiver's importer handles duplicates
     * gracefully via SkippedDuplicate. Optimize later if size matters.
     */
    fun exportSingle(confluence: Essence.Confluence): Envelope {
        val referenced = confluence.confluenceSets
            .flatMap { it.set }
            .distinctBy { it.name }
            .map { EnvelopeMapper.toWire(it) }
        return Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = referenced,
            confluences = listOf(EnvelopeMapper.toWire(confluence)),
        )
    }

    fun exportSingle(stone: AwakeningStone): Envelope = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        stones = listOf(EnvelopeMapper.toWire(stone)),
    )

    fun exportSingle(listing: Ability.Listing): Envelope = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        listings = listOf(EnvelopeMapper.toWire(listing)),
    )

    /**
     * Wraps a single status effect in an envelope. Used by the detail-screen
     * "Share" action; the receiver imports the entry, surfacing
     * SkippedDuplicate if they already have a same-named effect.
     */
    fun exportSingle(effect: ModelStatusEffect): Envelope = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        statusEffects = listOf(EnvelopeMapper.toWire(effect)),
    )

    /**
     * Wraps a single character build in an envelope. Builds reference essences,
     * confluences, and ability listings by name; the receiver resolves them
     * against their canonical + contributed data at import time. Unlike
     * `exportSingle(confluence)`, we do NOT bundle the referenced entities —
     * build shares are reference-only by design.
     */
    fun exportSingle(build: CharacterBuild): Envelope = Envelope(
        version = EnvelopeCodec.CurrentVersion,
        builds = listOf(EnvelopeMapper.toWire(build)),
    )
}
