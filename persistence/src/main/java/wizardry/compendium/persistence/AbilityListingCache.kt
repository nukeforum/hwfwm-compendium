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

    /**
     * Scan every effect description that contains a `{status:...}` token, pass
     * each to [rewrite], and persist any non-null returned value back to the
     * effect. The scan and the per-row updates run inside a single SQLite
     * transaction so the cascade is atomic — partial rewrites are not visible
     * to other readers, and a crash mid-cascade rolls the whole rewrite back.
     *
     * [rewrite] receives `(effectId, currentDescription)` and returns the new
     * description to write, or `null` to leave the row untouched. Returns the
     * number of rows actually updated.
     */
    fun bulkRewriteStatusTokens(
        rewrite: (effectId: Long, description: String) -> String?,
    ): Int
}
