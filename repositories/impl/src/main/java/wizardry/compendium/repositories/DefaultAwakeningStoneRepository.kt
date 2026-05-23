package wizardry.compendium.repositories

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import wizardry.compendium.repositories.detectAwakeningStoneConflicts
import wizardry.compendium.domain.manifestationsNotMatchingStones
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.toAwakeningStone
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.preferences.AwakeningStoneContributionsToggle
import wizardry.compendium.preferences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggle
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggleFlow

@Singleton
internal class DefaultAwakeningStoneRepository @Inject constructor(
    private val dataLoader: AwakeningStoneDataLoader,
    @param:Canonical private val canonicalCache: AwakeningStoneCache,
    @param:Contributions private val contributionsCache: AwakeningStoneCache,
    private val toggle: AwakeningStoneContributionsToggle,
    toggleFlow: AwakeningStoneContributionsToggleFlow,
    private val essenceRepository: EssenceRepository,
    private val essencesAsStonesToggle: EssencesAsAwakeningStonesToggle,
    essencesAsStonesToggleFlow: EssencesAsAwakeningStonesToggleFlow,
) : AwakeningStoneRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val awakeningStones: Flow<List<AwakeningStone>> = combine(
        toggleFlow.awakeningStoneContributionsEnabled,
        essencesAsStonesToggleFlow.essencesAsAwakeningStonesEnabled,
        essenceRepository.essences,
        invalidations,
    ) { _, _, _, _ -> getAwakeningStones() }

    override val conflicts: Flow<List<AwakeningStoneConflict>> = combine(
        toggleFlow.awakeningStoneContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }

    override suspend fun getAwakeningStones(): List<AwakeningStone> {
        val canonical = ensureCanonicalLoaded()
        val baseStones = mergedStones(canonical)
        if (!essencesAsStonesToggle.isEssencesAsAwakeningStonesEnabled) return baseStones

        val manifestations = essenceRepository.getEssences()
            .filterIsInstance<Essence.Manifestation>()
        val newcomers = manifestationsNotMatchingStones(manifestations, baseStones)
        if (newcomers.isEmpty()) return baseStones

        return (baseStones + newcomers.map { it.toAwakeningStone() }).sortedBy { it.name }
    }

    private fun mergedStones(canonical: List<AwakeningStone>): List<AwakeningStone> {
        if (!toggle.isAwakeningStoneContributionsEnabled) return canonical
        val contributions = contributionsCache.contents
        if (contributions.isEmpty()) return canonical
        if (detectAwakeningStoneConflicts(canonical, contributions).isNotEmpty()) return canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        return (merged + newOnes).sortedBy { it.name }
    }

    override suspend fun getConflicts(): List<AwakeningStoneConflict> {
        val canonical = ensureCanonicalLoaded()
        return detectAwakeningStoneConflicts(canonical, contributionsCache.contents)
    }

    override suspend fun getContributions(): List<AwakeningStone> = contributionsCache.contents

    override suspend fun saveAwakeningStoneContribution(
        stone: AwakeningStone,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
        val key = stone.name.normalized()
        val canonicalNames = canonical.map { it.name.normalized() }.toSet()
        val contributedNames = existing.map { it.name.normalized() }.toSet()
        if (key in canonicalNames || key in contributedNames) {
            return@withLock ContributionResult.Failure(
                "An awakening stone named \"${stone.name}\" already exists"
            )
        }
        contributionsCache.insert(stone)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun isContribution(name: String): Boolean = writeMutex.withLock {
        val key = name.normalized()
        contributionsCache.contents.any { it.name.normalized() == key }
    }

    override suspend fun deleteContribution(name: String): ContributionResult = writeMutex.withLock {
        val key = name.normalized()
        val existing = contributionsCache.contents
        val target = existing.firstOrNull { it.name.normalized() == key }
            ?: return@withLock ContributionResult.Failure(
                "No contribution exists for \"$name\""
            )
        val id = contributionsCache.findIdByName(target.name)
            ?: return@withLock ContributionResult.Failure("No contribution exists for \"$name\"")
        contributionsCache.deleteById(id)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun updateAwakeningStoneContribution(
        originalName: String,
        stone: AwakeningStone,
    ): ContributionResult = writeMutex.withLock {
        val originalKey = originalName.normalized()
        val newKey = stone.name.normalized()
        val existing = contributionsCache.contents
        val id = contributionsCache.findIdByName(originalName)
            ?: return@withLock ContributionResult.Failure(
                "No contributed awakening stone named \"$originalName\""
            )
        // If the name is changing, check for collisions in canonical and other contributions.
        if (originalKey != newKey) {
            val canonical = ensureCanonicalLoaded()
            val canonicalNames = canonical.map { it.name.normalized() }.toSet()
            val contributedNames = existing.filter { it.name.normalized() != originalKey }
                .map { it.name.normalized() }.toSet()
            if (newKey in canonicalNames || newKey in contributedNames) {
                return@withLock ContributionResult.Failure(
                    "An awakening stone named \"${stone.name}\" already exists"
                )
            }
        }
        contributionsCache.update(id, stone)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()

    private suspend fun ensureCanonicalLoaded(): List<AwakeningStone> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadAwakeningStoneData()
        canonicalCache.replaceAll(loaded)
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
