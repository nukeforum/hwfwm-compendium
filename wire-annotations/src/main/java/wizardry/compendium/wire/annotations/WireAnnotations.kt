package wizardry.compendium.wire.annotations

/**
 * Marks the wire envelope class. Exactly one class per project should carry this annotation.
 * The KSP processor walks reachable types from the annotated class to compute the schema.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WireFormat(val version: Int)

/**
 * Marks a property as part of the wire format.
 *
 * @param alias Single-letter (or short) name used on the wire.
 * @param previousAlias Comma-separated list of prior aliases this field used to be known by.
 *                      Auto-migration uses this to rename fields without manual migrators.
 *                      Empty when the field has never been renamed.
 * @param omitOnDefault If true, the property is omitted from the wire form when it equals
 *                      the Kotlin-declared default. Requires the property to have a default.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class WireField(
    val alias: String,
    val previousAlias: String = "",
    val omitOnDefault: Boolean = true,
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
