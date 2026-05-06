package wizardry.compendium.wire

/**
 * Identifies one of the five contribution domains carried by an [Envelope].
 *
 * Used by `Envelope.filteredTo` and `WireExporter.exportFiltered` to scope
 * exports/imports to a user-selected subset of domains. This is **not** the
 * same as `ImportResult.Domain`, which has four values (manifestations and
 * confluences both bucket into `Essence` for import-result reporting).
 * `ContributionDomain` is the picker-side enum: one entry per envelope list.
 */
enum class ContributionDomain {
    Essences,
    Confluences,
    AwakeningStones,
    AbilityListings,
    StatusEffects,
}
