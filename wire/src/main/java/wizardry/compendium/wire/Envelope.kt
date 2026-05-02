package wizardry.compendium.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import wizardry.compendium.wire.annotations.WireFormat
import wizardry.compendium.wire.annotations.WireType

/**
 * Wire envelope for contribution import/export. The shape lives in this module
 * so the KSP processor can snapshot it from annotations alone.
 *
 * # Single source of truth for aliases
 *
 * `@SerialName` is the only place the wire alias is declared. The KSP
 * processor reads `@SerialName.value` to populate the schema snapshot;
 * kotlinx-serialization reads it at runtime to drive encode/decode. No
 * duplication.
 *
 * `@WireField` is *optional* — present only when a property has been
 * renamed (carries `previousAlias` for the diff engine).
 *
 * # Tier-3 status
 *
 * This is still a *seed* envelope — only Manifestation is fleshed out. The
 * remaining domain types (Confluence, Stone, Listing, Effect, Cost,
 * ConfluenceSet) and the model↔wire encoders/decoders land later in tier 3.
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
    @SerialName("v")
    val version: Int,

    @SerialName("e")
    val manifestations: List<Manifestation> = emptyList(),
)

@WireType(alias = "manifestation")
@Serializable
data class Manifestation(
    @SerialName("n")
    val name: String,

    @SerialName("k")
    val rankIndex: Int,

    @SerialName("r")
    val rarityIndex: Int,

    @SerialName("p")
    val propertyIndices: List<Int> = emptyList(),

    @SerialName("d")
    val description: String = "",

    @SerialName("x")
    val isRestricted: Boolean = false,
)
