package wizardry.compendium.wire

/**
 * Per-entry outcome of an envelope import.
 *
 * The importer never silently overwrites or skips. Every entity in the
 * incoming envelope produces exactly one ImportResult, surfaced to the user
 * after the import completes.
 *
 * # Why per-entry rather than per-envelope
 *
 * A single envelope often contains multiple contributions — some new, some
 * already known to the receiver, some malformed. A single status doesn't
 * capture that. Per-entry results let the UI show a "3 added, 1 skipped, 1
 * failed" summary with a drill-down list.
 */
sealed interface ImportResult {

    /** Identifying name of the entity (so the UI can display it). */
    val name: String

    /** Domain that this entity belongs to, for grouping in the UI summary. */
    val domain: Domain

    enum class Domain { Essence, AwakeningStone, AbilityListing, StatusEffect }

    /** Successfully written to the contributions DB. */
    data class Added(
        override val name: String,
        override val domain: Domain,
    ) : ImportResult

    /**
     * Skipped because an entity with the same name (or, for confluences, an
     * overlapping combination) already exists either in canonical or in the
     * receiver's contributions. Not an error — the user already has it.
     */
    data class SkippedDuplicate(
        override val name: String,
        override val domain: Domain,
        val reason: String,
    ) : ImportResult

    /**
     * Failed to import for a non-recoverable reason: malformed shape,
     * unresolved name reference, etc. The entity does NOT get written; the
     * user sees the reason and can act on it.
     */
    data class Failed(
        override val name: String,
        override val domain: Domain,
        val reason: String,
    ) : ImportResult
}

/**
 * Aggregate summary of an import for UI display.
 */
data class ImportSummary(
    val results: List<ImportResult>,
) {
    val added: List<ImportResult.Added> = results.filterIsInstance<ImportResult.Added>()
    val skipped: List<ImportResult.SkippedDuplicate> = results.filterIsInstance<ImportResult.SkippedDuplicate>()
    val failed: List<ImportResult.Failed> = results.filterIsInstance<ImportResult.Failed>()

    val totalCount: Int = results.size
    val addedCount: Int get() = added.size
    val skippedCount: Int get() = skipped.size
    val failedCount: Int get() = failed.size
}
