package wizardry.compendium

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.persistence.CharacterBuildCache
import wizardry.compendium.persistence.Contributions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCharacterBuildRepository @Inject constructor(
    @param:Contributions private val cache: CharacterBuildCache,
    private val essenceRepository: EssenceRepository,
    private val abilityListingRepository: AbilityListingRepository,
) : CharacterBuildRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val builds: Flow<List<CharacterBuild>> = combine(
        invalidations,
        essenceRepository.essences,
        abilityListingRepository.abilityListings,
    ) { _, _, _ -> getBuilds() }

    override suspend fun getBuilds(): List<CharacterBuild> = hydrate(cache.contents)

    override suspend fun getBuild(name: String): CharacterBuild? =
        hydrate(cache.contents.filter { it.name == name }).firstOrNull()

    override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult =
        writeMutex.withLock {
            val existing = cache.contents
            val replacement = existing.map { if (it.name == build.name) build else it }
            cache.contents = if (replacement.any { it.name == build.name }) {
                replacement
            } else {
                existing + build
            }
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    override suspend fun deleteContribution(name: String): ContributionResult =
        writeMutex.withLock {
            val existing = cache.contents
            if (existing.none { it.name == name }) {
                return@withLock ContributionResult.Failure(
                    "No contributed build named \"$name\""
                )
            }
            cache.contents = existing.filterNot { it.name == name }
            invalidations.update { it + 1 }
            ContributionResult.Success
        }

    private suspend fun hydrate(stale: List<CharacterBuild>): List<CharacterBuild> {
        if (stale.isEmpty()) return emptyList()
        val essencesByName = essenceRepository.getEssences().associateBy { it.name }
        val listingsByName = abilityListingRepository.getAbilityListings().associateBy { it.name }
        return stale.map { build -> build.hydrated(essencesByName, listingsByName) }
    }

    private fun CharacterBuild.hydrated(
        essencesByName: Map<String, Essence>,
        listingsByName: Map<String, Ability.Listing>,
    ): CharacterBuild {
        val rebornRacial = racialAbilities.mapNotNull { stale ->
            val resolved = listingsByName[stale.name]
            if (resolved == null) {
                Log.w(TAG, "build '$name' references unknown racial ability '${stale.name}' — dropping")
            }
            resolved
        }

        val rebornAttrs = setOf(Power, Speed, Spirit, Recovery).map { attr ->
            val absorbed = attr.essence ?: return@map attr
            val resolvedEssence = essencesByName[absorbed.essence.name] as? Essence.Manifestation
            if (resolvedEssence == null) {
                Log.w(TAG, "build '$name' references unknown essence '${absorbed.essence.name}' — dropping slot")
                return@map attr.cleared()
            }
            val resolvedAbilities = absorbed.abilities.mapNotNull { stale ->
                val listing = listingsByName[stale.name]
                if (listing == null) {
                    Log.w(TAG, "build '$name' references unknown ability '${stale.name}' — dropping")
                }
                listing?.let { resolvedListing ->
                    Ability.Acquired(
                        name = resolvedListing.name,
                        effects = resolvedListing.effects,
                        rank = stale.rank,
                        tier = stale.tier,
                        progress = stale.progress,
                        boundEssence = resolvedEssence,
                        listing = resolvedListing,
                    )
                }
            }
            attr.withAbsorbed(AbsorbedEssence(resolvedEssence, resolvedAbilities))
        }.toSet()

        return CharacterBuild(
            name = name,
            race = race,
            racialAbilities = rebornRacial,
            attributes = rebornAttrs,
        )
    }

    private fun Attribute.cleared(): Attribute = when (this) {
        is Attribute.Power -> Attribute.Power()
        is Attribute.Speed -> Attribute.Speed()
        is Attribute.Spirit -> Attribute.Spirit()
        is Attribute.Recovery -> Attribute.Recovery()
    }

    private fun Attribute.withAbsorbed(absorbed: AbsorbedEssence): Attribute = when (this) {
        is Attribute.Power -> Attribute.Power(essence = absorbed)
        is Attribute.Speed -> Attribute.Speed(essence = absorbed)
        is Attribute.Spirit -> Attribute.Spirit(essence = absorbed)
        is Attribute.Recovery -> Attribute.Recovery(essence = absorbed)
    }

    private companion object {
        const val TAG = "BuildRepo"
    }
}
