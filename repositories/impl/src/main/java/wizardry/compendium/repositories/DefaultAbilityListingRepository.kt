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
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.essences.dataloader.AbilityListingDataLoader
import wizardry.compendium.repositories.detectAbilityListingConflicts
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityRef
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.RaceTemplateDatabase
import wizardry.compendium.preferences.AbilityListingContributionsToggle
import wizardry.compendium.preferences.AbilityListingContributionsToggleFlow

@Singleton
internal class DefaultAbilityListingRepository @Inject constructor(
    private val dataLoader: AbilityListingDataLoader,
    @param:Canonical private val canonicalCache: AbilityListingCache,
    @param:Contributions private val contributionsCache: AbilityListingCache,
    @param:Contributions private val characterBuildDatabase: CharacterBuildDatabase,
    @param:Contributions private val raceTemplateDatabase: RaceTemplateDatabase,
    private val toggle: AbilityListingContributionsToggle,
    toggleFlow: AbilityListingContributionsToggleFlow,
) : AbilityListingRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val abilityListings: Flow<List<Ability.Listing>> = combine(
        toggleFlow.abilityListingContributionsEnabled,
        invalidations,
    ) { _, _ -> getAbilityListings() }.flowOn(Dispatchers.IO)

    override val conflicts: Flow<List<AbilityListingConflict>> = combine(
        toggleFlow.abilityListingContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }.flowOn(Dispatchers.IO)

    override suspend fun getAbilityListings(): List<Ability.Listing> = withContext(Dispatchers.IO) {
        val canonical = ensureCanonicalLoaded()
        if (!toggle.isAbilityListingContributionsEnabled) return@withContext canonical
        val contributions = contributionsCache.contents
        if (contributions.isEmpty()) return@withContext canonical
        if (detectAbilityListingConflicts(canonical, contributions).isNotEmpty()) return@withContext canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        (merged + newOnes).sortedBy { it.name }
    }

    override suspend fun getConflicts(): List<AbilityListingConflict> = withContext(Dispatchers.IO) {
        val canonical = ensureCanonicalLoaded()
        detectAbilityListingConflicts(canonical, contributionsCache.contents)
    }

    override suspend fun getContributions(): List<Ability.Listing> =
        withContext(Dispatchers.IO) { contributionsCache.contents }

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
                "An ability named \"${listing.name}\" already exists"
            )
        }
        contributionsCache.insert(listing)
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

    override suspend fun updateAbilityListingContribution(
        originalName: String,
        listing: Ability.Listing,
    ): ContributionResult = writeMutex.withLock {
        val id = contributionsCache.findIdByName(originalName)
            ?: return@withLock ContributionResult.Failure(
                "No contributed ability named \"$originalName\""
            )
        if (!listing.name.equals(originalName, ignoreCase = true)) {
            val canonical = ensureCanonicalLoaded()
            val key = listing.name.normalized()
            val canonicalNames = canonical.map { it.name.normalized() }.toSet()
            val contributedNames = contributionsCache.identified
                .map { it.listing.name.normalized() }
                .toSet() - originalName.normalized()
            if (key in canonicalNames || key in contributedNames) {
                return@withLock ContributionResult.Failure(
                    "An ability named \"${listing.name}\" already exists"
                )
            }
        }
        contributionsCache.update(id, listing)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun checkDeleteImpact(name: String): DeleteImpact = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val id = contributionsCache.findIdByName(name) ?: return@withLock DeleteImpact()
            val ref = RefCodec.encodeAbilityRef(AbilityRef.Contributed(id))
            val builds = characterBuildDatabase.buildsReferencingListingRef(ref)
            val templates = raceTemplateDatabase.templatesReferencingListingRef(ref)
            DeleteImpact(referencingBuilds = builds, referencingRaceTemplates = templates)
        }
    }

    private suspend fun ensureCanonicalLoaded(): List<Ability.Listing> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadAbilityListingData()
        canonicalCache.replaceAll(loaded)
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
