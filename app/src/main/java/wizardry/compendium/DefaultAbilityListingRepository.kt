package wizardry.compendium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingContributionsToggleFlow
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.dataloader.AbilityListingDataLoader
import wizardry.compendium.essences.detectAbilityListingConflicts
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.AbilityListingContributionsToggle
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAbilityListingRepository @Inject constructor(
    private val dataLoader: AbilityListingDataLoader,
    @param:Canonical private val canonicalCache: AbilityListingCache,
    @param:Contributions private val contributionsCache: AbilityListingCache,
    private val toggle: AbilityListingContributionsToggle,
    toggleFlow: AbilityListingContributionsToggleFlow,
) : AbilityListingRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val abilityListings: Flow<List<Ability.Listing>> = combine(
        toggleFlow.abilityListingContributionsEnabled,
        invalidations,
    ) { _, _ -> getAbilityListings() }

    override val conflicts: Flow<List<AbilityListingConflict>> = combine(
        toggleFlow.abilityListingContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }

    override suspend fun getAbilityListings(): List<Ability.Listing> {
        val canonical = ensureCanonicalLoaded()
        if (!toggle.isAbilityListingContributionsEnabled) return canonical
        val contributions = contributionsCache.contents
        if (contributions.isEmpty()) return canonical
        if (detectAbilityListingConflicts(canonical, contributions).isNotEmpty()) return canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        return (merged + newOnes).sortedBy { it.name }
    }

    override suspend fun getConflicts(): List<AbilityListingConflict> {
        val canonical = ensureCanonicalLoaded()
        return detectAbilityListingConflicts(canonical, contributionsCache.contents)
    }

    override suspend fun saveAbilityListingContribution(
        listing: Ability.Listing,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
        val key = listing.name.normalized()
        val canonicalNames = canonical.map { it.name.normalized() }.toSet()
        val contributedNames = existing.map { it.name.normalized() }.toSet()
        if (key in canonicalNames || key in contributedNames) {
            return@withLock ContributionResult.Failure(
                "An ability listing named \"${listing.name}\" already exists"
            )
        }
        contributionsCache.contents = existing + listing
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

    override suspend fun updateAbilityListingContribution(
        listing: Ability.Listing,
    ): ContributionResult = writeMutex.withLock {
        val key = listing.name.normalized()
        val existing = contributionsCache.contents
        if (existing.none { it.name.normalized() == key }) {
            return@withLock ContributionResult.Failure(
                "No contributed ability listing named \"${listing.name}\""
            )
        }
        contributionsCache.contents = existing.map {
            if (it.name.normalized() == key) listing else it
        }
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    private suspend fun ensureCanonicalLoaded(): List<Ability.Listing> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadAbilityListingData()
        canonicalCache.contents = loaded
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
