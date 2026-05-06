package wizardry.compendium.wire

/**
 * Returns a copy of this envelope with each domain list emptied unless
 * that domain is included in [selection]. The wire-format `version` is
 * preserved verbatim.
 *
 * Used by the Settings export/import flow to apply the user's per-domain
 * picker selection without mutating the original envelope.
 */
fun Envelope.filteredTo(selection: Set<ContributionDomain>): Envelope = copy(
    manifestations = if (ContributionDomain.Essences in selection) manifestations else emptyList(),
    confluences = if (ContributionDomain.Confluences in selection) confluences else emptyList(),
    stones = if (ContributionDomain.AwakeningStones in selection) stones else emptyList(),
    listings = if (ContributionDomain.AbilityListings in selection) listings else emptyList(),
    statusEffects = if (ContributionDomain.StatusEffects in selection) statusEffects else emptyList(),
)
