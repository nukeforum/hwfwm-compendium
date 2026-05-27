package wizardry.compendium.repositories

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.EssenceRef
import wizardry.compendium.domain.model.MalformedRefException
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.persistence.IdentifiedConfluence
import wizardry.compendium.persistence.RawConfluenceSet
import wizardry.compendium.preferences.EssenceContributionsToggle
import wizardry.compendium.preferences.EssenceContributionsToggleFlow

@Singleton
internal class DefaultEssenceRepository @Inject constructor(
    private val dataLoader: EssenceDataLoader,
    @param:Canonical private val canonicalCache: EssenceCache,
    @param:Contributions private val contributionsCache: EssenceCache,
    @param:Contributions private val essenceDatabase: EssenceDatabase,
    @param:Contributions private val characterBuildDatabase: CharacterBuildDatabase,
    private val toggle: EssenceContributionsToggle,
    toggleFlow: EssenceContributionsToggleFlow,
) : EssenceRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)
    @Volatile private var canonicalLoaded = false

    override val essences: Flow<List<Essence>> = combine(
        toggleFlow.essenceContributionsEnabled,
        invalidations,
    ) { _, _ -> getEssences() }.flowOn(Dispatchers.IO)

    override val conflicts: Flow<List<EssenceConflict>> = combine(
        toggleFlow.essenceContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }.flowOn(Dispatchers.IO)

    override suspend fun getEssences(): List<Essence> = withContext(Dispatchers.IO) {
        val canonical = readCanonical()
        if (!toggle.isEssenceContributionsEnabled) return@withContext canonical
        val contributions = readContributions(canonical)
        if (detectEssenceConflicts(canonical, contributions).isNotEmpty()) return@withContext canonical
        merge(canonical, contributions)
    }

    override suspend fun getConflicts(): List<EssenceConflict> = withContext(Dispatchers.IO) {
        val canonical = readCanonical()
        detectEssenceConflicts(canonical, readContributions(canonical))
    }

    override suspend fun getContributions(): List<Essence> = withContext(Dispatchers.IO) {
        val canonical = readCanonical()
        readContributions(canonical)
    }

    override suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult = writeMutex.withLock {
        val canonical = readCanonical()
        val existing = readContributions(canonical)
        val key = manifestation.name.normalized()
        val canonicalManifestationNames = canonical
            .filterIsInstance<Essence.Manifestation>()
            .map { it.name.normalized() }
            .toSet()
        val contributedNames = existing.map { it.name.normalized() }.toSet()
        if (key in canonicalManifestationNames || key in contributedNames) {
            return@withLock ContributionResult.Failure(
                "An essence named \"${manifestation.name}\" already exists"
            )
        }
        contributionsCache.insertManifestation(manifestation)
        invalidate()
        ContributionResult.Success
    }

    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = writeMutex.withLock {
        val canonical = readCanonical()
        val existing = readContributions(canonical)
        val key = confluence.name.normalized()
        val confluenceNames = (canonical + existing)
            .filterIsInstance<Essence.Confluence>()
            .map { it.name.normalized() }
            .toSet()
        if (key in confluenceNames) {
            return@withLock ContributionResult.Failure(
                "A confluence named \"${confluence.name}\" already exists"
            )
        }
        val combinationNames = confluence.confluenceSets
            .map { set -> set.set.map { it.name.normalized() }.toSet() }
        val combinationOwner = (canonical + existing)
            .filterIsInstance<Essence.Confluence>()
            .firstOrNull { conf ->
                conf.confluenceSets.any { set ->
                    set.set.map { it.name.normalized() }.toSet() in combinationNames
                }
            }
        if (combinationOwner != null) {
            return@withLock ContributionResult.Failure(
                "That combination already produces ${combinationOwner.name}"
            )
        }
        val canonicalManifestationNames = canonical
            .filterIsInstance<Essence.Manifestation>()
            .map { it.name.normalized() }
            .toSet()
        val existingNames = existing.map { it.name.normalized() }.toSet()
        val manifestationsToAdd = referencedManifestations
            .distinctBy { it.name.normalized() }
            .filter {
                it.name.normalized() !in existingNames &&
                    it.name.normalized() !in canonicalManifestationNames
            }
        manifestationsToAdd.forEach { contributionsCache.insertManifestation(it) }
        // Now insert the confluence with encoded refs.
        contributionsCache.insertConfluence(
            name = confluence.name,
            isRestricted = confluence.isRestricted,
            sets = confluence.confluenceSets.map { encodeSet(it, canonical) },
        )
        invalidate()
        ContributionResult.Success
    }

    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = writeMutex.withLock {
        val canonical = readCanonical()
        val existing = readContributions(canonical)
        val combinationNames = combination.set.map { it.name.normalized() }.toSet()
        val duplicateOwner = (canonical + existing)
            .filterIsInstance<Essence.Confluence>()
            .firstOrNull { conf ->
                conf.confluenceSets.any { set ->
                    set.set.map { it.name.normalized() }.toSet() == combinationNames
                }
            }
        if (duplicateOwner != null) {
            return@withLock ContributionResult.Failure(
                "That combination already produces ${duplicateOwner.name}"
            )
        }
        // Pre-existing combination set: append the new combination and replace.
        val source = (existing.firstOrNull { it.name == target.name } as? Essence.Confluence)
            ?: target
        val updated = source.copy(
            confluenceSets = source.confluenceSets + combination,
        )
        val canonicalManifestationNames = canonical
            .filterIsInstance<Essence.Manifestation>()
            .map { it.name.normalized() }
            .toSet()
        val existingNames = existing.map { it.name.normalized() }.toSet()
        val manifestationsToAdd = combination.set
            .distinctBy { it.name.normalized() }
            .filter {
                it.name.normalized() !in existingNames &&
                    it.name.normalized() !in canonicalManifestationNames
            }
        manifestationsToAdd.forEach { contributionsCache.insertManifestation(it) }
        // Replace the confluence row.
        val id = contributionsCache.findConfluenceIdByName(updated.name)
            ?: run {
                contributionsCache.insertConfluence(
                    name = updated.name,
                    isRestricted = updated.isRestricted,
                    sets = updated.confluenceSets.map { encodeSet(it, canonical) },
                )
                invalidate()
                return@withLock ContributionResult.Success
            }
        contributionsCache.updateConfluence(
            id = id,
            name = updated.name,
            isRestricted = updated.isRestricted,
            sets = updated.confluenceSets.map { encodeSet(it, canonical) },
        )
        invalidate()
        ContributionResult.Success
    }

    override suspend fun isContribution(name: String): Boolean = writeMutex.withLock {
        val key = name.normalized()
        contributionsCache.findManifestationIdByName(name) != null ||
            contributionsCache.findConfluenceIdByName(name) != null ||
            // also consider lowercase variants
            contributionsCache.identifiedManifestations.any { it.manifestation.name.normalized() == key } ||
            contributionsCache.identifiedConfluences.any { it.name.normalized() == key }
    }

    override suspend fun deleteContribution(name: String): ContributionResult = writeMutex.withLock {
        val key = name.normalized()
        // Try manifestation first.
        val manifestationId = contributionsCache.identifiedManifestations
            .firstOrNull { it.manifestation.name.normalized() == key }
            ?.id
        if (manifestationId != null) {
            // Check whether other contributed confluences reference this manifestation.
            val refToContr = "contr:$manifestationId"
            val isReferenced = contributionsCache.identifiedConfluences.any { conf ->
                conf.sets.any { set ->
                    set.essence1Ref == refToContr || set.essence2Ref == refToContr || set.essence3Ref == refToContr
                }
            }
            if (isReferenced) {
                return@withLock ContributionResult.Failure(
                    "\"$name\" is referenced by other contributed confluences"
                )
            }
            contributionsCache.deleteManifestationById(manifestationId)
            invalidate()
            return@withLock ContributionResult.Success
        }
        // Try confluence.
        val confluenceId = contributionsCache.identifiedConfluences
            .firstOrNull { it.name.normalized() == key }
            ?.id
        if (confluenceId != null) {
            contributionsCache.deleteConfluenceById(confluenceId)
            invalidate()
            return@withLock ContributionResult.Success
        }
        ContributionResult.Failure("No contribution exists for \"$name\"")
    }

    override suspend fun updateManifestationContribution(
        originalName: String,
        manifestation: Essence.Manifestation,
    ): ContributionResult = writeMutex.withLock {
        val id = contributionsCache.findManifestationIdByName(originalName)
            ?: return@withLock ContributionResult.Failure(
                "No contributed essence named \"$originalName\""
            )
        if (!manifestation.name.equals(originalName, ignoreCase = true)) {
            val collision = nameCollidesExcluding(manifestation.name, excludingOriginalName = originalName)
            if (collision != null) {
                return@withLock ContributionResult.Failure(collision)
            }
        }
        contributionsCache.updateManifestation(id, manifestation)
        invalidate()
        ContributionResult.Success
    }

    override suspend fun updateConfluenceContribution(
        originalName: String,
        confluence: Essence.Confluence,
    ): ContributionResult = writeMutex.withLock {
        val id = contributionsCache.findConfluenceIdByName(originalName)
            ?: return@withLock ContributionResult.Failure(
                "No contributed confluence named \"$originalName\""
            )
        if (!confluence.name.equals(originalName, ignoreCase = true)) {
            val collision = nameCollidesExcluding(confluence.name, excludingOriginalName = originalName)
            if (collision != null) {
                return@withLock ContributionResult.Failure(collision)
            }
        }
        val canonical = readCanonical()
        contributionsCache.updateConfluence(
            id = id,
            name = confluence.name,
            isRestricted = confluence.isRestricted,
            sets = confluence.confluenceSets.map { encodeSet(it, canonical) },
        )
        invalidate()
        ContributionResult.Success
    }

    private suspend fun nameCollidesExcluding(newName: String, excludingOriginalName: String): String? {
        val key = newName.normalized()
        val originalKey = excludingOriginalName.normalized()
        val canonical = readCanonical()
        val canonicalNames = canonical.map { it.name.normalized() }.toSet()
        if (key in canonicalNames) {
            return "An essence named \"$newName\" already exists"
        }
        val otherContributedManifestations = contributionsCache.identifiedManifestations
            .map { it.manifestation.name.normalized() }
            .toSet() - originalKey
        val otherContributedConfluences = contributionsCache.identifiedConfluences
            .map { it.name.normalized() }
            .toSet() - originalKey
        if (key in otherContributedManifestations || key in otherContributedConfluences) {
            return "An essence named \"$newName\" already exists"
        }
        return null
    }

    override suspend fun checkDeleteImpact(name: String): DeleteImpact = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val manifestationId = contributionsCache.findManifestationIdByName(name)
            if (manifestationId != null) {
                val ref = RefCodec.encodeEssenceRef(EssenceRef.Contributed(manifestationId))
                val builds = characterBuildDatabase.buildsReferencingEssenceRef(ref)
                val confluences = essenceDatabase.confluenceNamesReferencingEssenceRef(ref)
                return@withLock DeleteImpact(
                    referencingBuilds = builds,
                    referencingConfluenceSets = confluences,
                )
            }
            val confluenceId = contributionsCache.findConfluenceIdByName(name)
            if (confluenceId != null) {
                val ref = RefCodec.encodeEssenceRef(EssenceRef.Contributed(confluenceId))
                val builds = characterBuildDatabase.buildsReferencingEssenceRef(ref)
                return@withLock DeleteImpact(referencingBuilds = builds)
            }
            DeleteImpact()
        }
    }

    private fun invalidate() {
        invalidations.update { it + 1 }
    }

    private suspend fun readCanonical(): List<Essence> {
        if (!canonicalLoaded) {
            val existing = readCacheAsEssences(canonicalCache, fallbackCanonical = emptyList())
            if (existing.isNotEmpty()) {
                canonicalLoaded = true
                return existing
            }
            val loaded = dataLoader.loadEssenceData()
            canonicalCache.replaceAll(loaded)
            canonicalLoaded = true
            return loaded
        }
        return readCacheAsEssences(canonicalCache, fallbackCanonical = emptyList())
    }

    private fun readContributions(canonical: List<Essence>): List<Essence> =
        readCacheAsEssences(contributionsCache, fallbackCanonical = canonical)

    /**
     * Read raw manifestations and confluences from a cache and build domain
     * Essence objects, resolving confluence_set tagged refs against:
     *   - this cache's own manifestations by id (for `contr:<id>`)
     *   - [fallbackCanonical]'s manifestations by name (for `canon:<name>`)
     *
     * Refs that fail to resolve cause their set to be dropped; confluences with
     * zero remaining sets are dropped entirely.
     */
    private fun readCacheAsEssences(cache: EssenceCache, fallbackCanonical: List<Essence>): List<Essence> {
        val manifestations = cache.identifiedManifestations.map { it.manifestation }
        val manifestationsById = cache.identifiedManifestations
            .associate { it.id to it.manifestation }
        val canonicalManifestationsByName = fallbackCanonical
            .filterIsInstance<Essence.Manifestation>()
            .associateBy { it.name }
        // Within-cache canonical refs: when reading the canonical cache itself, refs
        // can also resolve against this cache's own manifestations by name.
        val selfManifestationsByName = manifestations.associateBy { it.name }

        val confluences = cache.identifiedConfluences.mapNotNull { raw ->
            hydrateConfluence(raw) { ref ->
                resolveMember(ref, manifestationsById, canonicalManifestationsByName, selfManifestationsByName)
            }
        }

        return (manifestations + confluences).sortedBy { it.name }
    }

    private fun resolveMember(
        ref: String,
        contributedManifestationsById: Map<Long, Essence.Manifestation>,
        canonicalByName: Map<String, Essence.Manifestation>,
        selfByName: Map<String, Essence.Manifestation>,
    ): Essence.Manifestation? = try {
        when (val decoded = RefCodec.decodeEssenceRef(ref)) {
            is EssenceRef.Canonical -> canonicalByName[decoded.name] ?: selfByName[decoded.name]
            is EssenceRef.Contributed -> contributedManifestationsById[decoded.id]
        }
    } catch (e: MalformedRefException) {
        null
    }

    private fun encodeSet(set: ConfluenceSet, canonical: List<Essence>): RawConfluenceSet {
        val members = set.set.sortedBy { it.name }
        require(members.size == 3) { "ConfluenceSet must have exactly 3 members" }
        return RawConfluenceSet(
            essence1Ref = encodeMemberRef(members[0].name, canonical),
            essence2Ref = encodeMemberRef(members[1].name, canonical),
            essence3Ref = encodeMemberRef(members[2].name, canonical),
            isRestricted = set.isRestricted,
        )
    }

    private fun encodeMemberRef(name: String, canonical: List<Essence>): String {
        val contributedId = contributionsCache.findManifestationIdByName(name)
        if (contributedId != null) return RefCodec.encodeEssenceRef(EssenceRef.Contributed(contributedId))
        return RefCodec.encodeEssenceRef(EssenceRef.Canonical(name))
    }

    private fun merge(canonical: List<Essence>, contributions: List<Essence>): List<Essence> {
        if (contributions.isEmpty()) return canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        return (merged + newOnes).sortedBy { it.name }
    }

    private fun String.normalized(): String = trim().lowercase()
}
