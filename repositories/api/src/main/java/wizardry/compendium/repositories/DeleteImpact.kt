package wizardry.compendium.repositories

/**
 * The references that would be left orphaned by deleting a contribution.
 *
 * Each list holds the names of the entities that reference the contribution:
 * - [referencingBuilds] — character builds.
 * - [referencingConfluenceSets] — confluences whose confluence_sets include this essence.
 * - [referencingAbilityListings] — contributed ability listings whose descriptions
 *   contain a {status:NAME} token pointing at this status effect.
 * - [referencingRaceTemplates] — contributed race templates whose racial abilities
 *   include this ability listing.
 *
 * [isEmpty] returns true when no references exist; the calling screen then
 * skips the confirmation dialog and proceeds directly to delete.
 */
data class DeleteImpact(
    val referencingBuilds: List<String> = emptyList(),
    val referencingConfluenceSets: List<String> = emptyList(),
    val referencingAbilityListings: List<String> = emptyList(),
    val referencingRaceTemplates: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = referencingBuilds.isEmpty() &&
            referencingConfluenceSets.isEmpty() &&
            referencingAbilityListings.isEmpty() &&
            referencingRaceTemplates.isEmpty()
}
