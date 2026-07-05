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
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.RaceTemplateDatabase
import wizardry.compendium.persistence.RaceTemplateRefResolver

/**
 * Race templates live only in the @Contributions database (there is no
 * canonical seed), so this repository mirrors [DefaultCharacterBuildRepository]
 * minus the essence/attribute machinery: it decodes stored ability-listing refs
 * against the canonical and contributed listing caches at read time and encodes
 * them back (contr:<id> when the listing is a contribution, else canon:<name>)
 * at write time.
 */
@Singleton
internal class DefaultRaceTemplateRepository @Inject constructor(
    @param:Contributions private val database: RaceTemplateDatabase,
    @param:Contributions private val abilityListingContributionsCache: AbilityListingCache,
    private val abilityListingRepository: AbilityListingRepository,
) : RaceTemplateRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    private val resolver = RaceTemplateRefResolverImpl()

    override val raceTemplates: Flow<List<RaceTemplate>> = combine(
        invalidations,
        abilityListingRepository.abilityListings,
    ) { _, _ -> getRaceTemplates() }.flowOn(Dispatchers.IO)

    override suspend fun getRaceTemplates(): List<RaceTemplate> =
        withContext(Dispatchers.IO) { readAllResolved() }

    override suspend fun getRaceTemplate(name: String): RaceTemplate? =
        withContext(Dispatchers.IO) { readAllResolved().firstOrNull { it.name == name } }

    override suspend fun saveRaceTemplateContribution(template: RaceTemplate): ContributionResult =
        writeMutex.withLock {
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
        val snapshot = database.readSnapshot()
        if (snapshot.templates.isEmpty()) return emptyList()

        val canonicalListingsByName = abilityListingRepository.getAbilityListings().associateBy { it.name }
        val contributedListingsById = abilityListingContributionsCache.identified.associate { it.id to it.listing }

        val rawRacial = snapshot.racialAbilities.groupBy { it.templateName }

        return snapshot.templates.map { rawTemplate ->
            val racialAbilities = rawRacial[rawTemplate.name].orEmpty()
                .sortedBy { it.ordinal }
                .mapNotNull { row ->
                    resolveListingRef(row.listingRef, canonicalListingsByName, contributedListingsById, contextName = rawTemplate.name)
                }
            RaceTemplate(name = rawTemplate.name, racialAbilities = racialAbilities)
        }.sortedBy { it.name }
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

    private companion object {
        const val TAG = "RaceTemplateRepo"
    }
}
