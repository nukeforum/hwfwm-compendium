package wizardry.compendium.wire.ksp

import kotlinx.serialization.Serializable

/**
 * Serializable description of the wire format at a single version.
 *
 * This is the "source of truth" the lint check compares against. The committed
 * `wire/wire-schemas/v<N>.json` contains a snapshot serialized from this shape;
 * each build re-derives the snapshot from annotations and asserts equivalence.
 *
 * ## Assumption: one envelope per project
 * We assume exactly one class is annotated `@WireFormat` per project. Multiple
 * envelopes would mean we'd need a snapshot-per-envelope and the lock check
 * would need to handle them separately — defer until we actually have that need.
 *
 * ## Over-compensation: stable ordering everywhere
 * The processor sorts types and fields alphabetically before snapshotting so
 * that two unrelated builds (with different processing order) produce the same
 * lock file. This is paranoid given KSP's resolver typically returns symbols in
 * a stable order, but the cost of sorting is negligible and saves us from
 * mysterious lock churn if KSP's behavior changes between versions.
 *
 * ## Forward compat note
 * Adding new optional fields to these data classes is fine — the lock file
 * format itself is allowed to evolve as we add more diff-detection capability
 * (e.g., adding `nullable` or `genericArgs` later). When adding such fields,
 * ensure they default to a sensible empty/false value so old lock files still
 * parse cleanly.
 */
@Serializable
data class SchemaSnapshot(
    val version: Int,
    val envelope: String,
    val types: List<TypeEntry>,
    val enums: List<EnumEntry>,
)

/**
 * A wire type — typically a `data class` referenced from the envelope, possibly
 * transitively. Each property serialized to the wire is recorded here with the
 * info we need to detect compatible vs. breaking changes:
 *
 * - `alias` rename: detect via current alias not in old, but `previousAliases`
 *   contains the old alias → mechanical migrator.
 * - Field add: alias appears in new but not old. If the new field has a
 *   default, we can mechanically backfill it; if not, we need a manual migrator.
 * - Field remove: alias appears in old but not new → drop on read; lossy but
 *   automatic since data classes ignore unknown JSON keys when configured.
 *
 * ## Assumption: type identity is FQN
 * We track types by fully-qualified name. Renames at the Kotlin level break
 * snapshot continuity even if the wire shape is unchanged. That's a feature
 * (forces the dev to think) and a bug (a benign internal rename triggers churn).
 * If this becomes annoying, add an explicit `@WireType(stableId = "...")`
 * convention so wire identity is decoupled from Kotlin identity.
 */
@Serializable
data class TypeEntry(
    val fqn: String,
    val typeAlias: String,
    val fields: List<FieldEntry>,
)

@Serializable
data class FieldEntry(
    val name: String,
    /**
     * Wire alias. Sourced from `@SerialName.value` on the property — kotlinx-
     * serialization's annotation is the single source of truth for the alias
     * since the runtime needs it anyway.
     */
    val alias: String,
    /**
     * Comma-separated previous aliases. Empty when never renamed.
     * Stored as a single string (matching the annotation's shape) rather than
     * a list to keep the diff engine's job simpler — it just looks for
     * membership.
     */
    val previousAlias: String,
    /** FQN of the property type, with type-arguments rendered as `<...>`. */
    val type: String,
    /**
     * True if the property has a Kotlin-declared default. Required to know
     * whether a newly-added field can be mechanically migrated (default
     * backfill) or needs a manual migrator.
     *
     * ## Caveat: KSP can't always tell us the default *value*
     * We record presence-of-default but not the literal value. This is enough
     * for the v1 migration story: when an old envelope is missing a field
     * that the new model has a default for, decoding fills the default in
     * automatically. If we ever need the literal value baked into a generated
     * migrator (e.g., for transformations) we'd need to also capture and parse
     * the default expression — KSP exposes this as a `KSValueArgument` for
     * some cases but not all (especially function-call defaults).
     */
    val hasDefault: Boolean,
)

/**
 * Enum-or-sealed-objects type whose declaration order is wire-stable. The
 * snapshot stores the ordered entry list; the lock check fails if entries are
 * reordered or removed. Adding a new entry at the END is non-breaking; adding
 * elsewhere or removing requires a version bump + migrator.
 *
 * ## Why we accept sealed-interface-of-objects too
 * The project's `Property`, `AbilityType`, `Resource`, `Amount` are
 * `sealed interface`s with `object` subtypes rather than `enum class`es. Treat
 * them identically here. Detection is by annotation presence (`@WireEnum`)
 * rather than by declaration kind so the developer's intent is explicit.
 */
@Serializable
data class EnumEntry(
    val fqn: String,
    val entries: List<String>,
)
