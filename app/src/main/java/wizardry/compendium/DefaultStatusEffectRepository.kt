package wizardry.compendium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectContributionsToggleFlow
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.dataloader.StatusEffectDataLoader
import wizardry.compendium.essences.detectStatusEffectConflicts
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.persistence.StatusEffectContributionsToggle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultStatusEffectRepository @Inject constructor(
    private val dataLoader: StatusEffectDataLoader,
    @param:Canonical private val canonicalCache: StatusEffectCache,
    @param:Contributions private val contributionsCache: StatusEffectCache,
    private val toggle: StatusEffectContributionsToggle,
    toggleFlow: StatusEffectContributionsToggleFlow,
) : StatusEffectRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val statusEffects: Flow<List<StatusEffect>> = combine(
        toggleFlow.statusEffectContributionsEnabled,
        invalidations,
    ) { _, _ -> getStatusEffects() }

    override val conflicts: Flow<List<StatusEffectConflict>> = combine(
        toggleFlow.statusEffectContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }

    override suspend fun getStatusEffects(): List<StatusEffect> {
        val canonical = ensureCanonicalLoaded()
        if (!toggle.isStatusEffectContributionsEnabled) return canonical
        val contributions = contributionsCache.contents
        if (contributions.isEmpty()) return canonical
        if (detectStatusEffectConflicts(canonical, contributions).isNotEmpty()) return canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        return (merged + newOnes).sortedBy { it.name }
    }

    override suspend fun getConflicts(): List<StatusEffectConflict> {
        val canonical = ensureCanonicalLoaded()
        return detectStatusEffectConflicts(canonical, contributionsCache.contents)
    }

    override suspend fun getContributions(): List<StatusEffect> = contributionsCache.contents

    override suspend fun saveStatusEffectContribution(
        effect: StatusEffect,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
        val key = effect.name.normalized()
        val canonicalNames = canonical.map { it.name.normalized() }.toSet()
        val contributedNames = existing.map { it.name.normalized() }.toSet()
        if (key in canonicalNames || key in contributedNames) {
            return@withLock ContributionResult.Failure(
                "A status effect named \"${effect.name}\" already exists"
            )
        }
        contributionsCache.contents = existing + effect
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

    override suspend fun updateStatusEffectContribution(
        effect: StatusEffect,
    ): ContributionResult = writeMutex.withLock {
        val key = effect.name.normalized()
        val existing = contributionsCache.contents
        if (existing.none { it.name.normalized() == key }) {
            return@withLock ContributionResult.Failure(
                "No contributed status effect named \"${effect.name}\""
            )
        }
        contributionsCache.contents = existing.map {
            if (it.name.normalized() == key) effect else it
        }
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    private suspend fun ensureCanonicalLoaded(): List<StatusEffect> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadStatusEffectData()
        canonicalCache.contents = loaded
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
