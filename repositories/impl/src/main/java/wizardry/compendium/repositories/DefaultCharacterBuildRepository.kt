package wizardry.compendium.repositories

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.domain.model.AbilityRef
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.EssenceRef
import wizardry.compendium.domain.model.MalformedRefException
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.BuildRefResolver
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.IdentifiedConfluence

@Singleton
internal class DefaultCharacterBuildRepository @Inject constructor(
    @param:Contributions private val database: CharacterBuildDatabase,
    @param:Contributions private val abilityListingContributionsCache: AbilityListingCache,
    @param:Contributions private val essenceContributionsCache: EssenceCache,
    private val essenceRepository: EssenceRepository,
    private val abilityListingRepository: AbilityListingRepository,
) : CharacterBuildRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    private val resolver = BuildRefResolverImpl()

    override val builds: Flow<List<CharacterBuild>> = combine(
        invalidations,
        essenceRepository.essences,
        abilityListingRepository.abilityListings,
    ) { _, _, _ -> getBuilds() }

    override suspend fun getBuilds(): List<CharacterBuild> = readAllResolved()

    override suspend fun getBuild(name: String): CharacterBuild? =
        readAllResolved().firstOrNull { it.name == name }

    override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult =
        writeMutex.withLock {
            database.upsert(build, resolver)
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    override suspend fun deleteContribution(name: String): ContributionResult =
        writeMutex.withLock {
            val existing = database.readAllBuilds().any { it.name == name }
            if (!existing) {
                return@withLock ContributionResult.Failure(
                    "No contributed build named \"$name\""
                )
            }
            database.deleteByName(name)
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    // --- Read path: decode raw rows and resolve refs --------------------

    private suspend fun readAllResolved(): List<CharacterBuild> {
        val rawBuilds = database.readAllBuilds()
        if (rawBuilds.isEmpty()) return emptyList()

        val canonicalListingsByName = abilityListingRepository.getAbilityListings().associateBy { it.name }
        val canonicalEssencesByName = essenceRepository.getEssences().associateBy { it.name }
        val contributedListingsById = abilityListingContributionsCache.identified.associate { it.id to it.listing }
        val contributedManifestationsById = essenceContributionsCache.identifiedManifestations
            .associate { it.id to it.manifestation }
        val contributedConfluencesById = essenceContributionsCache.identifiedConfluences
            .associate { it.id to it }

        val rawRacial = database.readAllRacialAbilities().groupBy { it.buildName }
        val rawAttrs = database.readAllAttributes().groupBy { it.buildName }
        val rawAcquired = database.readAllAcquiredAbilities()
            .groupBy { it.buildName to it.attributeKind }

        return rawBuilds.map { rawBuild ->
            val racialAbilities = rawRacial[rawBuild.name].orEmpty()
                .sortedBy { it.ordinal }
                .mapNotNull { row ->
                    resolveListingRef(row.listingRef, canonicalListingsByName, contributedListingsById, contextName = rawBuild.name)
                }

            val attrs = AttributeKind.entries.map { kind ->
                val attrRow = rawAttrs[rawBuild.name].orEmpty().firstOrNull { it.kind == kind.name }
                    ?: return@map kind.empty()

                val resolvedEssence: Essence? = resolveEssenceRef(
                    attrRow.essenceRef,
                    canonicalEssencesByName,
                    contributedManifestationsById,
                    contributedConfluencesById,
                    contextName = rawBuild.name,
                )
                if (resolvedEssence == null) {
                    return@map kind.empty()
                }

                val abilities = rawAcquired[rawBuild.name to kind.name].orEmpty()
                    .sortedBy { it.ordinal }
                    .mapNotNull { acquiredRow ->
                        val resolvedListing = resolveListingRef(
                            acquiredRow.listingRef,
                            canonicalListingsByName,
                            contributedListingsById,
                            contextName = rawBuild.name,
                        ) ?: return@mapNotNull null
                        Ability.Acquired(
                            name = resolvedListing.name,
                            effects = resolvedListing.effects,
                            rank = wizardry.compendium.domain.model.Rank.valueOf(acquiredRow.rank),
                            tier = acquiredRow.tier.toInt(),
                            progress = acquiredRow.progress.toFloat(),
                            boundEssence = resolvedEssence,
                            listing = resolvedListing,
                        )
                    }

                kind.withEssence(AbsorbedEssence(resolvedEssence, abilities))
            }.toSet()

            CharacterBuild(
                name = rawBuild.name,
                race = rawBuild.race,
                racialAbilities = racialAbilities,
                attributes = attrs,
            )
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
                if (it == null) Log.w(TAG, "build '$contextName' references unknown canonical ability '${decoded.name}' — dropping")
            }
            is AbilityRef.Contributed -> contributedById[decoded.id].also {
                if (it == null) Log.w(TAG, "build '$contextName' references unknown contributed ability id ${decoded.id} — dropping")
            }
        }
    } catch (e: MalformedRefException) {
        Log.w(TAG, "build '$contextName' has malformed listing ref '${e.raw}' — dropping")
        null
    }

    private fun resolveEssenceRef(
        raw: String,
        canonicalByName: Map<String, Essence>,
        contributedManifestationsById: Map<Long, Essence.Manifestation>,
        contributedConfluencesById: Map<Long, IdentifiedConfluence>,
        contextName: String,
    ): Essence? = try {
        when (val decoded = RefCodec.decodeEssenceRef(raw)) {
            is EssenceRef.Canonical -> canonicalByName[decoded.name].also {
                if (it == null) Log.w(TAG, "build '$contextName' references unknown canonical essence '${decoded.name}' — dropping slot")
            }
            is EssenceRef.Contributed -> {
                contributedManifestationsById[decoded.id]
                    ?: hydrateContributedConfluence(decoded.id, contributedConfluencesById, canonicalByName, contributedManifestationsById, contextName)
                    ?: run {
                        Log.w(TAG, "build '$contextName' references unknown contributed essence id ${decoded.id} — dropping slot")
                        null
                    }
            }
        }
    } catch (e: MalformedRefException) {
        Log.w(TAG, "build '$contextName' has malformed essence ref '${e.raw}' — dropping slot")
        null
    }

    /**
     * Hydrate a contributed confluence from its raw identified form. The contributed
     * confluence's confluence_sets reference essences via tagged ref strings (canon:
     * or contr:); resolve each set member.
     */
    private fun hydrateContributedConfluence(
        id: Long,
        confluences: Map<Long, IdentifiedConfluence>,
        canonicalEssencesByName: Map<String, Essence>,
        contributedManifestationsById: Map<Long, Essence.Manifestation>,
        contextName: String,
    ): Essence.Confluence? {
        val raw = confluences[id] ?: return null
        val sets = raw.sets.mapNotNull { setRow ->
            val members = listOf(setRow.essence1Ref, setRow.essence2Ref, setRow.essence3Ref).mapNotNull { memberRef ->
                try {
                    when (val decoded = RefCodec.decodeEssenceRef(memberRef)) {
                        is EssenceRef.Canonical -> canonicalEssencesByName[decoded.name] as? Essence.Manifestation
                        is EssenceRef.Contributed -> contributedManifestationsById[decoded.id]
                    }
                } catch (e: MalformedRefException) {
                    Log.w(TAG, "confluence '${raw.name}' has malformed essence ref '${e.raw}' — dropping set")
                    null
                }
            }
            if (members.size != 3) {
                Log.w(TAG, "confluence '${raw.name}' set has unresolvable members — dropping set")
                null
            } else {
                ConfluenceSet(
                    set = members.toSet(),
                    isRestricted = setRow.isRestricted,
                )
            }
        }
        if (sets.isEmpty()) {
            Log.w(TAG, "build '$contextName' references contributed confluence '${raw.name}' but no sets resolve — dropping slot")
            return null
        }
        return Essence.of(name = raw.name, restricted = raw.isRestricted, confluences = sets.toTypedArray()) as Essence.Confluence
    }

    // --- Write path: BuildRefResolverImpl -------------------------------

    private inner class BuildRefResolverImpl : BuildRefResolver {
        override fun encodeListing(listing: Ability.Listing): String {
            val contributedId = abilityListingContributionsCache.findIdByName(listing.name)
            val ref = if (contributedId != null) {
                AbilityRef.Contributed(contributedId)
            } else {
                AbilityRef.Canonical(listing.name)
            }
            return RefCodec.encodeAbilityRef(ref)
        }

        override fun encodeEssence(essence: Essence): String {
            val name = essence.name
            val contributedId = essenceContributionsCache.findManifestationIdByName(name)
                ?: essenceContributionsCache.findConfluenceIdByName(name)
            val ref = if (contributedId != null) {
                EssenceRef.Contributed(contributedId)
            } else {
                EssenceRef.Canonical(name)
            }
            return RefCodec.encodeEssenceRef(ref)
        }
    }

    private enum class AttributeKind {
        Power, Speed, Spirit, Recovery;

        fun empty(): Attribute = when (this) {
            Power -> Attribute.Power()
            Speed -> Attribute.Speed()
            Spirit -> Attribute.Spirit()
            Recovery -> Attribute.Recovery()
        }

        fun withEssence(absorbed: AbsorbedEssence): Attribute = when (this) {
            Power -> Attribute.Power(essence = absorbed)
            Speed -> Attribute.Speed(essence = absorbed)
            Spirit -> Attribute.Spirit(essence = absorbed)
            Recovery -> Attribute.Recovery(essence = absorbed)
        }
    }

    private companion object {
        const val TAG = "BuildRepo"
    }
}
