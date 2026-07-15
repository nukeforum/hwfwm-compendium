package wizardry.compendium.repositories

import android.util.Log
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
import wizardry.compendium.domain.model.AbilityRef
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.MalformedRefException
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.essences.dataloader.RaceTemplateDataLoader
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.RaceTemplateDatabase
import wizardry.compendium.persistence.RaceTemplateRefResolver
import wizardry.compendium.persistence.RawRaceTemplateSnapshot

/**
 * Race templates merge two sources, like the other canonical-backed entities:
 * the canonical seed (`races.csv`, lazily written into the @Canonical DB on
 * first read — the same ensureCanonicalLoaded pattern the essence / ability /
 * status-effect repositories use) and the @Contributions DB. Both store
 * ability-listing refs as tagged strings that are decoded against the
 * canonical and contributed listing caches at read time; canonical templates
 * only ever hold `canon:<name>` refs, contributions encode `contr:<id>` when
 * the listing is itself a contribution. Canonical templates are read-only:
 * saves that would create a new contribution under a canonical name are
 * rejected and deletes only see the contributions DB. A contribution that
 * nonetheless shares a canonical name (e.g. restored from an old backup)
 * shadows the canonical entry, mirroring the merged-view precedence of the
 * other repositories, and may be updated in place.
 */
@Singleton
internal class DefaultRaceTemplateRepository @Inject constructor(
    private val dataLoader: RaceTemplateDataLoader,
    @param:Canonical private val canonicalDatabase: RaceTemplateDatabase,
    @param:Contributions private val database: RaceTemplateDatabase,
    @param:Contributions private val abilityListingContributionsCache: AbilityListingCache,
    private val abilityListingRepository: AbilityListingRepository,
) : RaceTemplateRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    private val resolver = RaceTemplateRefResolverImpl()

    /** Canonical templates reference canonical listings by name, always. */
    private val canonicalSeedResolver = object : RaceTemplateRefResolver {
        override fun encodeListing(listing: Ability.Listing): String =
            RefCodec.encodeAbilityRef(AbilityRef.Canonical(listing.name))
    }

    override val raceTemplates: Flow<List<RaceTemplate>> = combine(
        invalidations,
        abilityListingRepository.abilityListings,
    ) { _, _ -> getRaceTemplates() }.flowOn(Dispatchers.IO)

    override suspend fun getRaceTemplates(): List<RaceTemplate> =
        withContext(Dispatchers.IO) { readAllResolved() }

    override suspend fun getRaceTemplate(name: String): RaceTemplate? =
        withContext(Dispatchers.IO) { readAllResolved().firstOrNull { it.name == name } }

    override suspend fun isContribution(name: String): Boolean = writeMutex.withLock {
        val key = name.normalized()
        database.readAllRaceTemplates().any { it.name.normalized() == key }
    }

    override suspend fun saveRaceTemplateContribution(template: RaceTemplate): ContributionResult =
        writeMutex.withLock {
            val key = template.name.normalized()
            val canonicalNames = ensureCanonicalLoaded().templates.map { it.name.normalized() }.toSet()
            val shadowingContribution = database.readAllRaceTemplates().any { it.name.normalized() == key }
            if (key in canonicalNames && !shadowingContribution) {
                return@withLock ContributionResult.Failure(
                    "A canonical race template named \"${template.name}\" already exists"
                )
            }
            database.upsert(template, resolver)
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    override suspend fun deleteContribution(name: String): ContributionResult =
        writeMutex.withLock {
            val existing = database.readAllRaceTemplates().any { it.name == name }
            if (!existing) {
                return@withLock ContributionResult.Failure(
                    "No contributed race template named \"$name\""
                )
            }
            database.deleteByName(name)
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    // --- Read path: decode raw rows and resolve refs --------------------

    private suspend fun readAllResolved(): List<RaceTemplate> {
        val canonicalSnapshot = ensureCanonicalLoaded()
        val contributionsSnapshot = database.readSnapshot()
        if (canonicalSnapshot.templates.isEmpty() && contributionsSnapshot.templates.isEmpty()) {
            return emptyList()
        }

        val canonicalListingsByName = abilityListingRepository.getAbilityListings().associateBy { it.name }
        val contributedListingsById = abilityListingContributionsCache.identified.associate { it.id to it.listing }

        val canonical = resolveSnapshot(canonicalSnapshot, canonicalListingsByName, contributedListingsById)
        val contributions = resolveSnapshot(contributionsSnapshot, canonicalListingsByName, contributedListingsById)

        val contributedNames = contributions.map { it.name.normalized() }.toSet()
        return (canonical.filterNot { it.name.normalized() in contributedNames } + contributions)
            .sortedBy { it.name }
    }

    private fun resolveSnapshot(
        snapshot: RawRaceTemplateSnapshot,
        canonicalListingsByName: Map<String, Ability.Listing>,
        contributedListingsById: Map<Long, Ability.Listing>,
    ): List<RaceTemplate> {
        val rawRacial = snapshot.racialAbilities.groupBy { it.templateName }
        return snapshot.templates.map { rawTemplate ->
            val racialAbilities = rawRacial[rawTemplate.name].orEmpty()
                .sortedBy { it.ordinal }
                .mapNotNull { row ->
                    resolveListingRef(row.listingRef, canonicalListingsByName, contributedListingsById, contextName = rawTemplate.name)
                }
            RaceTemplate(name = rawTemplate.name, racialAbilities = racialAbilities)
        }
    }

    private fun resolveListingRef(
        raw: String,
        canonicalByName: Map<String, Ability.Listing>,
        contributedById: Map<Long, Ability.Listing>,
        contextName: String,
    ): Ability.Listing? = try {
        when (val decoded = RefCodec.decodeAbilityRef(raw)) {
            is AbilityRef.Canonical -> canonicalByName[decoded.name].also {
                if (it == null) Log.w(TAG, "race template '$contextName' references unknown canonical ability '${decoded.name}' — dropping")
            }
            is AbilityRef.Contributed -> contributedById[decoded.id].also {
                if (it == null) Log.w(TAG, "race template '$contextName' references unknown contributed ability id ${decoded.id} — dropping")
            }
        }
    } catch (e: MalformedRefException) {
        Log.w(TAG, "race template '$contextName' has malformed listing ref '${e.raw}' — dropping")
        null
    }

    // --- Canonical seed --------------------------------------------------

    /**
     * Lazily seed the @Canonical race-template tables from the data loader on
     * first read, mirroring `ensureCanonicalLoaded` in the other repositories.
     * A concurrent double-seed is benign: [RaceTemplateDatabase.writeAll]
     * replaces all rows in one transaction with the same data.
     */
    private suspend fun ensureCanonicalLoaded(): RawRaceTemplateSnapshot {
        val current = canonicalDatabase.readSnapshot()
        if (current.templates.isNotEmpty()) return current
        val loaded = dataLoader.loadRaceTemplateData()
        if (loaded.isEmpty()) return current
        canonicalDatabase.writeAll(loaded, canonicalSeedResolver)
        return canonicalDatabase.readSnapshot()
    }

    // --- Write path: RaceTemplateRefResolverImpl ------------------------

    private inner class RaceTemplateRefResolverImpl : RaceTemplateRefResolver {
        override fun encodeListing(listing: Ability.Listing): String {
            val contributedId = abilityListingContributionsCache.findIdByName(listing.name)
            val ref = if (contributedId != null) {
                AbilityRef.Contributed(contributedId)
            } else {
                AbilityRef.Canonical(listing.name)
            }
            return RefCodec.encodeAbilityRef(ref)
        }
    }

    private fun String.normalized(): String = trim().lowercase()

    private companion object {
        const val TAG = "RaceTemplateRepo"
    }
}
