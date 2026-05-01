package wizardry.compendium.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import wizardry.compendium.wire.annotations.WireField
import wizardry.compendium.wire.annotations.WireFormat
import wizardry.compendium.wire.annotations.WireType

/**
 * Wire envelope for contribution import/export. The shape lives in this module
 * so the KSP processor can snapshot it from annotations alone.
 *
 * # Tier-1 status
 *
 * This is a *seed* envelope — only enough types are present to drive the
 * processor and produce a meaningful initial snapshot. Tier 2 will fill in the
 * remaining domain types (Confluence, Stone, Listing, Effect, Cost,
 * ConfluenceSet) along with the diff engine. Adapter generation is also
 * deferred; for now the kotlinx-serialization adapters are hand-written via
 * `@SerialName`.
 *
 * # Why both `@WireField` and `@SerialName`?
 *
 * `@WireField` is consumed by the KSP processor (compile time) for the schema
 * lock and future codegen. `@SerialName` is consumed by kotlinx-serialization
 * (runtime) to drive the JSON encode/decode. They MUST match. Tier 3 will
 * have the processor generate the kotlinx adapters from `@WireField` so the
 * developer only writes the alias once.
 *
 * # Naming convention for aliases
 *
 * Single letters where unambiguous within the type's scope, longer aliases
 * (still short — 2-3 chars) where collisions threaten clarity. The alias
 * mapping is documented in `docs/contributions-import-export.md`.
 */
@WireFormat(version = 1)
@WireType(alias = "envelope")
@Serializable
data class Envelope(
    @WireField(alias = "v")
    @SerialName("v")
    val version: Int,

    @WireField(alias = "e")
    @SerialName("e")
    val manifestations: List<Manifestation> = emptyList(),
)

@WireType(alias = "manifestation")
@Serializable
data class Manifestation(
    @WireField(alias = "n")
    @SerialName("n")
    val name: String,

    @WireField(alias = "k")
    @SerialName("k")
    val rankIndex: Int,

    @WireField(alias = "r")
    @SerialName("r")
    val rarityIndex: Int,

    @WireField(alias = "p")
    @SerialName("p")
    val propertyIndices: List<Int> = emptyList(),

    @WireField(alias = "d")
    @SerialName("d")
    val description: String = "",

    @WireField(alias = "x")
    @SerialName("x")
    val isRestricted: Boolean = false,
)
