package wizardry.compendium.wire.annotations

/**
 * Marks the wire envelope class. Exactly one class per project should carry this annotation.
 * The KSP processor walks reachable types from the annotated class to compute the schema.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WireFormat(val version: Int)

/**
 * Annotates a property with wire-format metadata that doesn't fit on
 * `@SerialName`. Currently this is just the rename history.
 *
 * # Why this is split from `@SerialName`
 *
 * The wire alias itself comes from kotlinx-serialization's `@SerialName` —
 * which is already required for the runtime to encode/decode against the
 * alias. Re-declaring the alias on `@WireField` would be silly redundancy.
 *
 * `@WireField` exists only to carry information kotlinx-serialization
 * doesn't model:
 *
 * - `previousAlias`: comma-separated list of aliases this field used to
 *   carry. The diff engine reads this to detect a rename and emit a
 *   migrator instead of treating the change as remove+add.
 *
 * `omitOnDefault` is intentionally NOT a per-field setting. The wire codec
 * uses `Json { encodeDefaults = false }` globally so any field with a
 * declared default is omitted when it equals that default. If we ever need
 * field-level override, that's the place to add it.
 *
 * `@WireField` is *optional* — most properties don't need it. Only renamed
 * fields require this annotation.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class WireField(
    val previousAlias: String = "",
)

/**
 * Marks a data class as a wire-format type. Optional — the processor can also pick up
 * unmarked classes referenced from the envelope. Use this when you want to set a custom
 * type alias or document the type's role.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WireType(val alias: String = "")

/**
 * Marks an enum or sealed-interface-of-objects whose ordinal/declaration order is part
 * of the wire contract. The processor records the ordered entry list in the schema
 * snapshot; reordering or removing entries fails the build (lock check) without an
 * accompanying migration.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WireEnum
