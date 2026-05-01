package wizardry.compendium

import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import wizardry.compendium.essences.detectAwakeningStoneConflicts
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.AwakeningStoneContributionsToggle
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAwakeningStoneRepository @Inject constructor(
    private val dataLoader: AwakeningStoneDataLoader,
    @param:Canonical private val canonicalCache: AwakeningStoneCache,
    @param:Contributions private val contributionsCache: AwakeningStoneCache,
    private val toggle: AwakeningStoneContributionsToggle,
    toggleFlow: AwakeningStoneContributionsToggleFlow,
) : AwakeningStoneRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val awakeningStones: Flow<List<AwakeningStone>> = combine(
        toggleFlow.awakeningStoneContributionsEnabled,
        invalidations,
    ) { _, _ -> getAwakeningStones() }

    override val conflicts: Flow<List<AwakeningStoneConflict>> = combine(
        toggleFlow.awakeningStoneContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }

    override suspend fun getAwakeningStones(): List<AwakeningStone> {
        val canonical = ensureCanonicalLoaded()
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
        contributionsCache.contents = existing + stone
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
        if (existing.none { it.name.normalized() == key }) {
            return@withLock ContributionResult.Failure(
                "No contribution exists for \"$name\""
            )
        }
        contributionsCache.contents = existing.filterNot { it.name.normalized() == key }
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun updateAwakeningStoneContribution(
        stone: AwakeningStone,
    ): ContributionResult = writeMutex.withLock {
        val key = stone.name.normalized()
        val existing = contributionsCache.contents
        if (existing.none { it.name.normalized() == key }) {
            return@withLock ContributionResult.Failure(
                "No contributed awakening stone named \"${stone.name}\""
            )
        }
        contributionsCache.contents = existing.map {
            if (it.name.normalized() == key) stone else it
        }
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    private suspend fun ensureCanonicalLoaded(): List<AwakeningStone> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadAwakeningStoneData()
        canonicalCache.contents = loaded
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
