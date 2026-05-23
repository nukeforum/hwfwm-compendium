package wizardry.compendium.persistence

import wizardry.compendium.domain.model.Ability

data class IdentifiedListing(val id: Long, val listing: Ability.Listing)

interface AbilityListingCache {
    /** Read all listings, paired with their persistent ids. */
    val identified: List<IdentifiedListing>

    /**
     * Convenience accessor returning just the domain listings (no ids).
     */
    val contents: List<Ability.Listing>
        get() = identified.map { it.listing }

    /** Insert a new contribution, returns its assigned id. */
    fun insert(listing: Ability.Listing): Long

    /**
     * Update an existing contribution identified by [id]. If the new name
     * differs from the row's current name, the row's `name` column is
     * updated. Effects are fully replaced.
     */
    fun update(id: Long, listing: Ability.Listing)

    /** Delete the contribution with the given id. */
    fun deleteById(id: Long)

    /** Look up the id for a contribution by exact name match. */
    fun findIdByName(name: String): Long?

    /** Replace all rows with the given listings, assigning fresh ids. Used by data loader / wire import. */
    fun replaceAll(listings: List<Ability.Listing>)

    /** Effect rows whose description contains a {status:...} token. Pair: (effectId, description). */
    fun selectEffectsWithStatusTokens(): List<Pair<Long, String>>

    /** Update a single effect's description by effect id. */
    fun updateEffectDescription(effectId: Long, description: String)
}
