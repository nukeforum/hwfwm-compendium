package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.Ability

interface AbilityListingRepository {
    val abilityListings: Flow<List<Ability.Listing>>

    suspend fun getAbilityListings(): List<Ability.Listing>

    suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult
}
