package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.domain.model.Ability

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

    /**
     * Update a contributed ability listing identified by [originalName]. If
     * [listing.name] differs from [originalName], the contribution is renamed.
     * Collision checks run before the write: returns Failure if the new name
     * conflicts with any canonical or contributed listing.
     */
    suspend fun updateAbilityListingContribution(
        originalName: String,
        listing: Ability.Listing,
    ): ContributionResult

    /**
     * Returns the references that would be broken by deleting the contribution
     * named [name]. An empty impact means deletion is safe.
     */
    suspend fun checkDeleteImpact(name: String): DeleteImpact
}
