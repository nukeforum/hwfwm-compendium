package wizardry.compendium.wire

import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Essence

/**
 * Applies a decoded `Envelope` to the receiver's contribution stores.
 *
 * # Order of operations
 *
 * 1. **Manifestations first.** Confluences reference manifestations by name;
 *    if both are present in the envelope, the manifestations must be in the
 *    DB before the confluence is processed. Manifestations from canonical
 *    or pre-existing contributions are equally valid as references.
 * 2. **Confluences second.** Each confluence resolves its name references
 *    against the receiver's full view (canonical + contributions, which now
 *    includes the just-imported manifestations).
 * 3. **Awakening stones and ability listings** in any order — they're
 *    self-contained and don't reference each other.
 *
 * # Conflict handling
 *
 * The importer never overwrites. Each entry is one of:
 *
 * - `Added` — repo returned `ContributionResult.Success`.
 * - `SkippedDuplicate` — repo returned `Failure` with a "already exists"
 *   shape. We don't try to distinguish "name conflict" vs "combination
 *   conflict at save time"; the repo's failure message is what we surface.
 * - `Failed` — the entry was malformed (bad enum index, unresolved name
 *   reference) or the repo returned an unexpected failure.
 *
 * # Why not run conflict detection ourselves
 *
 * We could pre-check for conflicts before calling the repo, but the repo's
 * save-time validation already handles the same cases (name uniqueness in
 * canonical+contributions, combination uniqueness for confluences). Letting
 * the repo be the gatekeeper keeps the conflict logic in one place.
 *
 * # No transaction
 *
 * Imports are not atomic. If the import fails partway through, prior
 * entries are already written. This is OK because:
 * 1. Each entry's write is idempotent — if you re-import after a partial
 *    failure, already-written entries surface as `SkippedDuplicate`.
 * 2. The user already saw a per-entry summary, so partial state is
 *    visible.
 *
 * If we ever need atomicity, we'd thread a transactional facade through
 * each repo. Premature for v1.
 */
class WireImporter(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
) {

    suspend fun import(envelope: Envelope): ImportSummary {
        val results = mutableListOf<ImportResult>()

        // Manifestations first. Even if a manifestation collides, we still
        // proceed to confluences — those can reference the *existing*
        // manifestation just as well as the freshly-imported one.
        for (wire in envelope.manifestations) {
            results += importManifestation(wire)
        }

        for (wire in envelope.confluences) {
            results += importConfluence(wire)
        }

        for (wire in envelope.stones) {
            results += importStone(wire)
        }

        for (wire in envelope.listings) {
            results += importListing(wire)
        }

        for (wire in envelope.statusEffects) {
            results += importStatusEffect(wire)
        }

        return ImportSummary(results)
    }

    private suspend fun importManifestation(wire: Manifestation): ImportResult {
        val name = wire.name
        return try {
            val model = EnvelopeMapper.toModel(wire)
            when (val r = essenceRepository.saveManifestationContribution(model)) {
                is ContributionResult.Success -> ImportResult.Added(name, ImportResult.Domain.Essence)
                is ContributionResult.Failure -> ImportResult.SkippedDuplicate(
                    name = name,
                    domain = ImportResult.Domain.Essence,
                    reason = r.message,
                )
            }
        } catch (e: WireDecodeException) {
            ImportResult.Failed(name, ImportResult.Domain.Essence, e.message ?: "decode failed")
        }
    }

    private suspend fun importConfluence(wire: Confluence): ImportResult {
        val name = wire.name
        return try {
            // Resolve manifestation references against the current
            // canonical+contributions view. Calls `getEssences()` once per
            // confluence import, which is fine for typical envelope sizes
            // (single-digit confluences); could cache if we ever process
            // hundred-confluence bundles.
            val view = essenceRepository.getEssences().filterIsInstance<Essence.Manifestation>()
                .associateBy { it.name.lowercase() }
            val model = EnvelopeMapper.toModel(wire) { needle ->
                view[needle.lowercase()]
            }
            when (val r = essenceRepository.saveConfluenceContribution(
                confluence = model,
                referencedManifestations = model.confluenceSets.flatMap { it.set }.distinct(),
            )) {
                is ContributionResult.Success -> ImportResult.Added(name, ImportResult.Domain.Essence)
                is ContributionResult.Failure -> ImportResult.SkippedDuplicate(
                    name = name,
                    domain = ImportResult.Domain.Essence,
                    reason = r.message,
                )
            }
        } catch (e: WireDecodeException) {
            ImportResult.Failed(name, ImportResult.Domain.Essence, e.message ?: "decode failed")
        }
    }

    private suspend fun importStone(wire: Stone): ImportResult {
        val name = wire.name
        return try {
            val model = EnvelopeMapper.toModel(wire)
            when (val r = awakeningStoneRepository.saveAwakeningStoneContribution(model)) {
                is ContributionResult.Success -> ImportResult.Added(name, ImportResult.Domain.AwakeningStone)
                is ContributionResult.Failure -> ImportResult.SkippedDuplicate(
                    name = name,
                    domain = ImportResult.Domain.AwakeningStone,
                    reason = r.message,
                )
            }
        } catch (e: WireDecodeException) {
            ImportResult.Failed(name, ImportResult.Domain.AwakeningStone, e.message ?: "decode failed")
        }
    }

    private suspend fun importListing(wire: Listing): ImportResult {
        val name = wire.name
        return try {
            val model = EnvelopeMapper.toModel(wire)
            when (val r = abilityListingRepository.saveAbilityListingContribution(model)) {
                is ContributionResult.Success -> ImportResult.Added(name, ImportResult.Domain.AbilityListing)
                is ContributionResult.Failure -> ImportResult.SkippedDuplicate(
                    name = name,
                    domain = ImportResult.Domain.AbilityListing,
                    reason = r.message,
                )
            }
        } catch (e: WireDecodeException) {
            ImportResult.Failed(name, ImportResult.Domain.AbilityListing, e.message ?: "decode failed")
        }
    }

    private suspend fun importStatusEffect(wire: StatusEffect): ImportResult {
        val name = wire.name
        return try {
            val model = EnvelopeMapper.toModel(wire)
            when (val r = statusEffectRepository.saveStatusEffectContribution(model)) {
                is ContributionResult.Success -> ImportResult.Added(name, ImportResult.Domain.StatusEffect)
                is ContributionResult.Failure -> ImportResult.SkippedDuplicate(
                    name = name,
                    domain = ImportResult.Domain.StatusEffect,
                    reason = r.message,
                )
            }
        } catch (e: WireDecodeException) {
            ImportResult.Failed(name, ImportResult.Domain.StatusEffect, e.message ?: "decode failed")
        }
    }
}
