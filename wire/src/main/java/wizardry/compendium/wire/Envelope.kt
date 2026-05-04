package wizardry.compendium.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import wizardry.compendium.wire.annotations.WireFormat
import wizardry.compendium.wire.annotations.WireType

/**
 * Wire envelope for contribution import/export, v1.
 *
 * # Compactness conventions
 *
 * - Single-letter aliases via `@SerialName`. The KSP plugin reads these for
 *   the schema lock; kotlinx-serialization reads them for runtime encoding.
 * - Empty lists / falsy booleans omitted via `Json { encodeDefaults = false }`
 *   on the codec (see EnvelopeCodec).
 * - Fixed-shape sub-records (ConfluenceSet, Cost) encode as JSON arrays
 *   ("tuples") via custom serializers. Tuples avoid per-field key strings,
 *   saving roughly 25 chars per ConfluenceSet and 15 chars per Cost.
 * - Manifestations are referenced **by name only** inside Confluence
 *   combinations. The importer resolves names against the receiver's
 *   canonical+contributions DB; an unresolved name is a hard error for that
 *   confluence (other entries in the same envelope still import).
 *
 * # Why everything is `Int` instead of typed enum
 *
 * Wire types use raw integer indices for enum values rather than the model
 * enum types directly. Two reasons:
 * 1. kotlinx-serialization can't auto-encode our `sealed interface` "enums"
 *    (Property, AbilityType, Resource, Amount) as integers — those aren't
 *    `Serializable`. Treating them all as `Int` on the wire keeps the
 *    serialization story uniform.
 * 2. Mapping live in `EnvelopeMapper`, where we own the index tables.
 *    Decoupling the wire-form integer from the runtime sealed-type makes
 *    domain churn (e.g., adding a new Property) free for the wire format
 *    (just append to the table; old data still decodes).
 *
 * # Tier-3c status
 *
 * All four domains are present. Custom tuple serializers are wired up.
 * The companion `EnvelopeCodec` handles transport (gzip+base64+JSON);
 * `EnvelopeMapper` handles model↔wire conversion using `EnumIndex` tables.
 */
@WireFormat(version = 1)
@WireType(alias = "envelope")
@Serializable
data class Envelope(
    @SerialName("v")
    val version: Int,

    @SerialName("e")
    val manifestations: List<Manifestation> = emptyList(),

    @SerialName("c")
    val confluences: List<Confluence> = emptyList(),

    @SerialName("s")
    val stones: List<Stone> = emptyList(),

    @SerialName("a")
    val listings: List<Listing> = emptyList(),

    @SerialName("x")
    val statusEffects: List<StatusEffect> = emptyList(),
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

@WireType(alias = "confluence")
@Serializable
data class Confluence(
    @SerialName("n")
    val name: String,

    @SerialName("s")
    val combinations: List<ConfluenceSet>,

    @SerialName("x")
    val isRestricted: Boolean = false,
)

/**
 * 4-element tuple: [name1, name2, name3, restrictedFlag] where flag is 0 or 1.
 *
 * Manifestation references are **by name** — the receiver looks up the
 * referenced manifestations against canonical + contributions. The 4th slot
 * is the per-combination restricted flag.
 *
 * Why an Int (0/1) for the flag rather than Boolean: saves 4 chars per
 * un-restricted set vs. `false` on the wire (the typical case is
 * un-restricted), and we wanted *some* discriminator in the slot to keep
 * the tuple shape stable.
 */
@WireType(alias = "confluenceSet")
@Serializable(with = ConfluenceSetSerializer::class)
data class ConfluenceSet(
    val name1: String,
    val name2: String,
    val name3: String,
    val restrictedFlag: Int,
)

@WireType(alias = "stone")
@Serializable
data class Stone(
    @SerialName("n")
    val name: String,

    @SerialName("r")
    val rarityIndex: Int,
)

@WireType(alias = "listing")
@Serializable
data class Listing(
    @SerialName("n")
    val name: String,

    @SerialName("f")
    val effects: List<Effect>,
)

@WireType(alias = "effect")
@Serializable
data class Effect(
    @SerialName("k")
    val rankIndex: Int,

    @SerialName("t")
    val typeIndex: Int,

    @SerialName("p")
    val propertyIndices: List<Int> = emptyList(),

    @SerialName("c")
    val costs: List<Cost> = emptyList(),

    @SerialName("d")
    val description: String,

    @SerialName("o")
    val cooldown: String = "",

    @SerialName("q")
    val replacementKey: String = "",
)

@WireType(alias = "statusEffect")
@Serializable
data class StatusEffect(
    @SerialName("n")
    val name: String,

    @SerialName("t")
    val typeIndex: Int,

    @SerialName("p")
    val propertyIndices: List<Int> = emptyList(),

    @SerialName("k")
    val stackable: Boolean = false,

    @SerialName("d")
    val description: String = "",
)

/**
 * 3-element tuple: [kind, amountIndex, resourceIndex] where kind is "U"
 * (Upfront) or "O" (Ongoing). `Cost.None` is never written — empty cost
 * lists at the model level are encoded as omitted `c` arrays.
 */
@WireType(alias = "cost")
@Serializable(with = CostSerializer::class)
data class Cost(
    val kind: String,
    val amountIndex: Int,
    val resourceIndex: Int,
) {
    companion object {
        const val KIND_UPFRONT = "U"
        const val KIND_ONGOING = "O"
    }
}

/**
 * Serializes `ConfluenceSet` as a JSON array of length 4.
 *
 * Custom serializers in kotlinx-serialization are tedious but well-trodden;
 * the pattern below is "decode/encode against `JsonElement` directly because
 * the underlying format is a positional array, not a structured object."
 *
 * # Why we don't reuse `ListSerializer(JsonElement.serializer())`
 *
 * That would lose type discipline at the data-class level. With the custom
 * serializer the wire form is a 4-tuple, but at runtime we deal with a
 * typed `ConfluenceSet`. The few extra lines of decode/encode pay back in
 * call-site clarity.
 *
 * # Failure modes
 *
 * - Wrong array length → IllegalArgumentException with a specific message.
 *   Importer wraps these as per-entry `ImportResult.Failed`.
 * - Wrong types (e.g., a number where a string is expected) → kotlinx-
 *   serialization's own JsonDecoderException; same wrapping.
 */
internal object ConfluenceSetSerializer : KSerializer<ConfluenceSet> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ConfluenceSet") {
        element<String>("name1")
        element<String>("name2")
        element<String>("name3")
        element<Int>("restrictedFlag")
    }

    override fun serialize(encoder: Encoder, value: ConfluenceSet) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ConfluenceSetSerializer only supports JSON encoding")
        jsonEncoder.encodeJsonElement(
            JsonArray(
                listOf(
                    JsonPrimitive(value.name1),
                    JsonPrimitive(value.name2),
                    JsonPrimitive(value.name3),
                    JsonPrimitive(value.restrictedFlag),
                ),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): ConfluenceSet {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ConfluenceSetSerializer only supports JSON decoding")
        val arr: List<JsonElement> = jsonDecoder.decodeJsonElement().jsonArray
        require(arr.size == 4) { "ConfluenceSet wire tuple must have 4 elements, got ${arr.size}" }
        return ConfluenceSet(
            name1 = arr[0].jsonPrimitive.content,
            name2 = arr[1].jsonPrimitive.content,
            name3 = arr[2].jsonPrimitive.content,
            restrictedFlag = arr[3].jsonPrimitive.int,
        )
    }
}

/**
 * Serializes `Cost` as a JSON array of length 3.
 *
 * Format: `[kind, amountIndex, resourceIndex]` where kind is the literal
 * string "U" or "O".
 */
internal object CostSerializer : KSerializer<Cost> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Cost") {
        element<String>("kind")
        element<Int>("amountIndex")
        element<Int>("resourceIndex")
    }

    override fun serialize(encoder: Encoder, value: Cost) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("CostSerializer only supports JSON encoding")
        jsonEncoder.encodeJsonElement(
            JsonArray(
                listOf(
                    JsonPrimitive(value.kind),
                    JsonPrimitive(value.amountIndex),
                    JsonPrimitive(value.resourceIndex),
                ),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Cost {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("CostSerializer only supports JSON decoding")
        val arr: List<JsonElement> = jsonDecoder.decodeJsonElement().jsonArray
        require(arr.size == 3) { "Cost wire tuple must have 3 elements, got ${arr.size}" }
        val kind = arr[0].jsonPrimitive.content
        require(kind == Cost.KIND_UPFRONT || kind == Cost.KIND_ONGOING) {
            "Cost kind must be \"${Cost.KIND_UPFRONT}\" or \"${Cost.KIND_ONGOING}\", got \"$kind\""
        }
        return Cost(
            kind = kind,
            amountIndex = arr[1].jsonPrimitive.int,
            resourceIndex = arr[2].jsonPrimitive.int,
        )
    }
}

