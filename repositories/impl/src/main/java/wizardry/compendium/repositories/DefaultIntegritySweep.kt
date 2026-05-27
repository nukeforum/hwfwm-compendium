package wizardry.compendium.repositories

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wizardry.compendium.domain.model.AbilityRef
import wizardry.compendium.domain.model.EssenceRef
import wizardry.compendium.domain.model.MalformedRefException
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.IdentifiedConfluence
import wizardry.compendium.persistence.IdentifiedListing
import wizardry.compendium.persistence.RawBuildSnapshot

@Singleton
internal class DefaultIntegritySweep @Inject constructor(
    @param:Contributions private val abilityListingContributionsCache: AbilityListingCache,
    @param:Contributions private val essenceContributionsCache: EssenceCache,
    @param:Contributions private val characterBuildDatabase: CharacterBuildDatabase,
    private val abilityListingRepository: AbilityListingRepository,
    private val essenceRepository: EssenceRepository,
    private val statusEffectRepository: StatusEffectRepository,
) : IntegritySweep {

    override suspend fun run(): List<IntegrityIssue> = withContext(Dispatchers.IO) {
        // Snapshot every cache exactly once. CharacterBuildDatabase.readSnapshot
        // is itself transactional (see CharacterBuildDatabase), so the four
        // build tables are internally consistent. The essence/listing/status
        // reads are each individually transactional (per their respective
        // *Database.identified accessors) — cross-cache snapshots are still
        // eventually-consistent, but we no longer re-issue each read 2-3 times
        // across the sweep helpers.
        val canonicalListingNames = abilityListingRepository.getAbilityListings()
            .map { it.name }.toSet()
        val canonicalEssenceNames = essenceRepository.getEssences()
            .map { it.name }.toSet()
        val knownStatusNames = statusEffectRepository.getStatusEffects()
            .map { it.name.lowercase() }.toSet()
        val contributedListings = abilityListingContributionsCache.identified
        val contributedListingIds = contributedListings.map { it.id }.toSet()
        val contributedManifestationIds = essenceContributionsCache.identifiedManifestations
            .map { it.id }.toSet()
        val contributedConfluences = essenceContributionsCache.identifiedConfluences
        val contributedConfluenceIds = contributedConfluences.map { it.id }.toSet()
        val buildSnapshot = characterBuildDatabase.readSnapshot()

        val issues = mutableListOf<IntegrityIssue>()
        sweepBuildRefs(
            snapshot = buildSnapshot,
            canonicalListingNames = canonicalListingNames,
            canonicalEssenceNames = canonicalEssenceNames,
            contributedListingIds = contributedListingIds,
            contributedManifestationIds = contributedManifestationIds,
            contributedConfluenceIds = contributedConfluenceIds,
            out = issues,
        )
        sweepConfluenceSetRefs(
            contributedConfluences = contributedConfluences,
            canonicalEssenceNames = canonicalEssenceNames,
            contributedManifestationIds = contributedManifestationIds,
            out = issues,
        )
        sweepStatusTokens(
            contributedListings = contributedListings,
            knownStatusNames = knownStatusNames,
            out = issues,
        )
        issues
    }

    private fun sweepBuildRefs(
        snapshot: RawBuildSnapshot,
        canonicalListingNames: Set<String>,
        canonicalEssenceNames: Set<String>,
        contributedListingIds: Set<Long>,
        contributedManifestationIds: Set<Long>,
        contributedConfluenceIds: Set<Long>,
        out: MutableList<IntegrityIssue>,
    ) {
        snapshot.racialAbilities.forEach { row ->
            checkAbilityRef(
                raw = row.listingRef,
                location = "build '${row.buildName}' racial slot #${row.ordinal}",
                canonicalNames = canonicalListingNames,
                contributedIds = contributedListingIds,
                out = out,
            )
        }
        snapshot.acquiredAbilities.forEach { row ->
            checkAbilityRef(
                raw = row.listingRef,
                location = "build '${row.buildName}' ${row.attributeKind} ability #${row.ordinal}",
                canonicalNames = canonicalListingNames,
                contributedIds = contributedListingIds,
                out = out,
            )
        }
        snapshot.attributes.forEach { row ->
            checkEssenceRef(
                raw = row.essenceRef,
                location = "build '${row.buildName}' ${row.kind} slot",
                canonicalNames = canonicalEssenceNames,
                contributedManifestationIds = contributedManifestationIds,
                contributedConfluenceIds = contributedConfluenceIds,
                out = out,
            )
        }
    }

    private fun sweepConfluenceSetRefs(
        contributedConfluences: List<IdentifiedConfluence>,
        canonicalEssenceNames: Set<String>,
        contributedManifestationIds: Set<Long>,
        out: MutableList<IntegrityIssue>,
    ) {
        contributedConfluences.forEach { conf ->
            conf.sets.forEachIndexed { idx, set ->
                listOf(
                    "essence1" to set.essence1Ref,
                    "essence2" to set.essence2Ref,
                    "essence3" to set.essence3Ref,
                ).forEach { (label, ref) ->
                    val location = "confluence '${conf.name}' set #$idx $label"
                    try {
                        when (val decoded = RefCodec.decodeEssenceRef(ref)) {
                            is EssenceRef.Canonical -> {
                                if (decoded.name !in canonicalEssenceNames) {
                                    out += IntegrityIssue.OrphanedCanonicalRef(
                                        location,
                                        decoded.name,
                                        IntegrityIssue.OrphanedCanonicalRef.Kind.Essence,
                                    )
                                }
                            }
                            is EssenceRef.Contributed -> {
                                if (decoded.id !in contributedManifestationIds) {
                                    out += IntegrityIssue.OrphanedContributedRef(
                                        location,
                                        decoded.id,
                                        IntegrityIssue.OrphanedContributedRef.Kind.Essence,
                                    )
                                }
                            }
                        }
                    } catch (e: MalformedRefException) {
                        out += IntegrityIssue.MalformedRef(location, e.raw)
                    }
                }
            }
        }
    }

    private fun sweepStatusTokens(
        contributedListings: List<IdentifiedListing>,
        knownStatusNames: Set<String>,
        out: MutableList<IntegrityIssue>,
    ) {
        val tokenRegex = Regex("""\{status:([^}]+)\}""")
        contributedListings.forEach { identified ->
            identified.listing.effects.forEachIndexed { ordinal, effect ->
                tokenRegex.findAll(effect.description).forEach { match ->
                    val statusName = match.groupValues[1]
                    if (statusName.lowercase() !in knownStatusNames) {
                        out += IntegrityIssue.OrphanedStatusToken(
                            abilityName = identified.listing.name,
                            effectOrdinal = ordinal.toLong(),
                            missingStatusName = statusName,
                        )
                    }
                }
            }
        }
    }

    private fun checkAbilityRef(
        raw: String,
        location: String,
        canonicalNames: Set<String>,
        contributedIds: Set<Long>,
        out: MutableList<IntegrityIssue>,
    ) {
        try {
            when (val decoded = RefCodec.decodeAbilityRef(raw)) {
                is AbilityRef.Canonical -> {
                    if (decoded.name !in canonicalNames) {
                        out += IntegrityIssue.OrphanedCanonicalRef(
                            location,
                            decoded.name,
                            IntegrityIssue.OrphanedCanonicalRef.Kind.Ability,
                        )
                    }
                }
                is AbilityRef.Contributed -> {
                    if (decoded.id !in contributedIds) {
                        out += IntegrityIssue.OrphanedContributedRef(
                            location,
                            decoded.id,
                            IntegrityIssue.OrphanedContributedRef.Kind.Ability,
                        )
                    }
                }
            }
        } catch (e: MalformedRefException) {
            out += IntegrityIssue.MalformedRef(location, e.raw)
        }
    }

    private fun checkEssenceRef(
        raw: String,
        location: String,
        canonicalNames: Set<String>,
        contributedManifestationIds: Set<Long>,
        contributedConfluenceIds: Set<Long>,
        out: MutableList<IntegrityIssue>,
    ) {
        try {
            when (val decoded = RefCodec.decodeEssenceRef(raw)) {
                is EssenceRef.Canonical -> {
                    if (decoded.name !in canonicalNames) {
                        out += IntegrityIssue.OrphanedCanonicalRef(
                            location,
                            decoded.name,
                            IntegrityIssue.OrphanedCanonicalRef.Kind.Essence,
                        )
                    }
                }
                is EssenceRef.Contributed -> {
                    if (decoded.id !in contributedManifestationIds && decoded.id !in contributedConfluenceIds) {
                        out += IntegrityIssue.OrphanedContributedRef(
                            location,
                            decoded.id,
                            IntegrityIssue.OrphanedContributedRef.Kind.Essence,
                        )
                    }
                }
            }
        } catch (e: MalformedRefException) {
            out += IntegrityIssue.MalformedRef(location, e.raw)
        }
    }
}
