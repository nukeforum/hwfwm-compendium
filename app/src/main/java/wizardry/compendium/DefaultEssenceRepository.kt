package wizardry.compendium

import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceContributionsToggleFlow
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceContributionsToggle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultEssenceRepository @Inject constructor(
    private val dataLoader: EssenceDataLoader,
    @param:Canonical private val canonicalCache: EssenceCache,
    @param:Contributions private val contributionsCache: EssenceCache,
    private val toggle: EssenceContributionsToggle,
    toggleFlow: EssenceContributionsToggleFlow,
) : EssenceRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val essences: Flow<List<Essence>> = combine(
        toggleFlow.essenceContributionsEnabled,
        invalidations,
    ) { _, _ -> getEssences() }

    override suspend fun getEssences(): List<Essence> {
        val canonical = ensureCanonicalLoaded()
        if (!toggle.isEssenceContributionsEnabled) return canonical
        return merge(canonical, contributionsCache.contents)
    }

    override suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
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
        contributionsCache.contents = existing + manifestation
        invalidate()
        ContributionResult.Success
    }

    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
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
        val existingNames = existing.map { it.name.normalized() }.toSet()
        val manifestationsToAdd = referencedManifestations
            .distinctBy { it.name.normalized() }
            .filter { it.name.normalized() !in existingNames }
        contributionsCache.contents = existing + manifestationsToAdd + confluence
        invalidate()
        ContributionResult.Success
    }

    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
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
        val withoutTarget = existing.filterNot { it.name == target.name }
        val source = (existing.firstOrNull { it.name == target.name } as? Essence.Confluence)
            ?: target
        val updated = source.copy(
            confluenceSets = source.confluenceSets + combination,
        )
        val existingNames = withoutTarget.map { it.name.normalized() }.toSet()
        val manifestationsToAdd = updated.confluenceSets
            .flatMap { it.set }
            .distinctBy { it.name.normalized() }
            .filter { it.name.normalized() !in existingNames }
        contributionsCache.contents = withoutTarget + manifestationsToAdd + updated
        invalidate()
        ContributionResult.Success
    }

    private fun invalidate() {
        invalidations.update { it + 1 }
    }

    private suspend fun ensureCanonicalLoaded(): List<Essence> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadEssenceData()
        canonicalCache.contents = loaded
        return loaded
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
