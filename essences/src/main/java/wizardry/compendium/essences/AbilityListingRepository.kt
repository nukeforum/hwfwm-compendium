package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.Ability

interface AbilityListingRepository {
    val abilityListings: Flow<List<Ability.Listing>>

    val conflicts: Flow<List<AbilityListingConflict>>

    suspend fun getAbilityListings(): List<Ability.Listing>

    /** Returns only user-contributed ability listings (no canonical entries). */
    suspend fun getContributions(): List<Ability.Listing>

    suspend fun getConflicts(): List<AbilityListingConflict>

    suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult

    suspend fun isContribution(name: String): Boolean

    suspend fun deleteContribution(name: String): ContributionResult

    suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult
}
