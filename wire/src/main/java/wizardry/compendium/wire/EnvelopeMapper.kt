package wizardry.compendium.wire

import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.CharacterBuild as ModelCharacterBuild
import wizardry.compendium.essences.model.Cost as ModelCost
import wizardry.compendium.essences.model.Effect as ModelEffect
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.ConfluenceSet as ModelConfluenceSet
import wizardry.compendium.essences.model.StatusEffect as ModelStatusEffect
import wizardry.compendium.wire.EnumIndex.abilityTypeFromIndex
import wizardry.compendium.wire.EnumIndex.amountFromIndex
import wizardry.compendium.wire.EnumIndex.propertyFromIndex
import wizardry.compendium.wire.EnumIndex.rankFromIndex
import wizardry.compendium.wire.EnumIndex.rarityFromIndex
import wizardry.compendium.wire.EnumIndex.resourceFromIndex
import wizardry.compendium.wire.EnumIndex.statusTypeFromIndex
import wizardry.compendium.wire.EnumIndex.toIndex
import kotlin.time.Duration

/**
 * Converts between domain model types and wire types.
 *
 * # The mapper is where domain knowledge lives
 *
 * The wire types are intentionally dumb: integers and short strings. The
 * mapper bridges to richer domain types (Essence.Manifestation, Cost.Upfront,
 * etc.) using the index tables in `EnumIndex`. Anything that requires
 * "knowledge of the domain" — which sealed-subtype to construct, how to
 * interpret an unset cooldown, how to resolve a manifestation reference —
 * lives here.
 *
 * # On-error behavior
 *
 * Mapper functions throw `WireDecodeException` for malformed wire data
 * (e.g., an unrecognized enum index, a confluence set referencing a
 * non-existent manifestation). The importer catches these and surfaces them
 * as `ImportResult.Failed` for the affected entity. The `CharacterBuild`
 * mapper is a deliberate exception: it silently drops unresolved essence
 * and ability-listing references (leaving empty slots / omitted abilities)
 * so a partially-importable build still round-trips, with the preview layer
 * responsible for surfacing missing references to the user.
 *
 * # Encoding: lossy by design
 *
 * The contribution form for awakening stones captures only `name + rarity`.
 * The model has more fields (description, properties, rank, effects) that
 * the form leaves at default. When *encoding*, we only emit what was
 * captured; on the receiving side, decoding produces a model object using
 * `AwakeningStone.of(name, rarity)` which fills the remaining fields with
 * the same defaults the contribute-form would have used. Round-trip
 * preserves the user's intent rather than the entire model surface.
 *
 * Same principle applies to manifestations and ability listings.
 */
object EnvelopeMapper {

    // -------------------------------------------------------------------------
    // Manifestation
    // -------------------------------------------------------------------------

    fun toWire(manifestation: Essence.Manifestation): Manifestation = Manifestation(
        name = manifestation.name,
        rankIndex = manifestation.rank.toIndex(),
        rarityIndex = manifestation.rarity.toIndex(),
        propertyIndices = manifestation.properties.map { it.toIndex() },
        description = manifestation.description,
        isRestricted = manifestation.isRestricted,
    )

    fun toModel(wire: Manifestation): Essence.Manifestation {
        return try {
            Essence.Manifestation(
                name = wire.name,
                rank = rankFromIndex(wire.rankIndex),
                rarity = rarityFromIndex(wire.rarityIndex),
                properties = wire.propertyIndices.map { propertyFromIndex(it) },
                description = wire.description,
                isRestricted = wire.isRestricted,
            )
        } catch (e: IllegalArgumentException) {
            throw WireDecodeException("Failed to decode manifestation '${wire.name}': ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Confluence
    // -------------------------------------------------------------------------

    fun toWire(confluence: Essence.Confluence): Confluence = Confluence(
        name = confluence.name,
        combinations = confluence.confluenceSets.map { set ->
            // The model stores the 3 manifestations as a Set, which doesn't
            // support indexing. Convert to list — typical Set impls in
            // Kotlin (LinkedHashSet from setOf) preserve insertion order, so
            // round-trip ordering is stable in practice.
            val members = set.set.toList()
            require(members.size == 3) {
                "Confluence '${confluence.name}' has a combination with ${members.size} members; expected 3"
            }
            ConfluenceSet(
                name1 = members[0].name,
                name2 = members[1].name,
                name3 = members[2].name,
                restrictedFlag = if (set.isRestricted) 1 else 0,
            )
        },
        isRestricted = confluence.isRestricted,
    )

    /**
     * Decode a wire confluence into a model confluence. Manifestation
     * references are resolved via `manifestationLookup` — typically a
     * function that consults canonical + contributions.
     *
     * Throws `WireDecodeException` if any referenced manifestation can't be
     * found. The importer wraps this as an `ImportResult.Failed` for the
     * confluence (other entries in the same envelope still import).
     */
    fun toModel(
        wire: Confluence,
        manifestationLookup: (name: String) -> Essence.Manifestation?,
    ): Essence.Confluence {
        val sets = wire.combinations.map { combo ->
            val a = manifestationLookup(combo.name1)
                ?: throw WireDecodeException("Confluence '${wire.name}' references unknown manifestation '${combo.name1}'")
            val b = manifestationLookup(combo.name2)
                ?: throw WireDecodeException("Confluence '${wire.name}' references unknown manifestation '${combo.name2}'")
            val c = manifestationLookup(combo.name3)
                ?: throw WireDecodeException("Confluence '${wire.name}' references unknown manifestation '${combo.name3}'")
            // Secondary ConfluenceSet ctor parameter name is `restricted`,
            // not `isRestricted` — minor naming quirk in the model that's
            // worth a comment so the next reader doesn't second-guess.
            ModelConfluenceSet(a, b, c, restricted = combo.restrictedFlag == 1)
        }.toSet()
        return Essence.Confluence(
            name = wire.name,
            confluenceSets = sets,
            isRestricted = wire.isRestricted,
        )
    }

    // -------------------------------------------------------------------------
    // Awakening stone
    // -------------------------------------------------------------------------

    fun toWire(stone: AwakeningStone): Stone = Stone(
        name = stone.name,
        rarityIndex = stone.rarity.toIndex(),
    )

    fun toModel(wire: Stone): AwakeningStone {
        return try {
            AwakeningStone.of(name = wire.name, rarity = rarityFromIndex(wire.rarityIndex))
        } catch (e: IllegalArgumentException) {
            throw WireDecodeException("Failed to decode awakening stone '${wire.name}': ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Ability listing
    // -------------------------------------------------------------------------

    fun toWire(listing: Ability.Listing): Listing = Listing(
        name = listing.name,
        effects = listing.effects.map { effectToWire(it) },
    )

    fun toModel(wire: Listing): Ability.Listing {
        return try {
            Ability.Listing(
                name = wire.name,
                effects = wire.effects.map { effectToModel(it) },
            )
        } catch (e: IllegalArgumentException) {
            throw WireDecodeException("Failed to decode ability '${wire.name}': ${e.message}", e)
        }
    }

    private fun effectToWire(effect: ModelEffect.AbilityEffect): Effect = Effect(
        rankIndex = effect.rank.toIndex(),
        typeIndex = effect.type.toIndex(),
        propertyIndices = effect.properties.map { it.toIndex() },
        // Strip Cost.None — it's a sentinel for "no cost," represented on
        // the wire as an absent `c` field rather than an explicit entry.
        costs = effect.cost
            .filterNot { it is ModelCost.None }
            .map { costToWire(it) },
        description = effect.description,
        cooldown = if (effect.cooldown == Duration.ZERO) "" else effect.cooldown.toString(),
        replacementKey = effect.replacementKey.orEmpty(),
    )

    private fun effectToModel(wire: Effect): ModelEffect.AbilityEffect {
        val costs = if (wire.costs.isEmpty()) {
            // Reconstruct the model's "no cost" sentinel.
            listOf(ModelCost.None)
        } else {
            wire.costs.map { costToModel(it) }
        }
        return ModelEffect.AbilityEffect(
            rank = rankFromIndex(wire.rankIndex),
            type = abilityTypeFromIndex(wire.typeIndex),
            properties = wire.propertyIndices.map { propertyFromIndex(it) },
            cost = costs,
            cooldown = parseCooldown(wire.cooldown),
            description = wire.description,
            replacementKey = wire.replacementKey.takeIf { it.isNotEmpty() },
        )
    }

    private fun costToWire(cost: ModelCost): Cost {
        // Both Upfront and Ongoing carry (amount, resource); the kind tag
        // discriminates. We never emit Cost.None — it's pre-filtered.
        return when (cost) {
            is ModelCost.Upfront -> Cost(
                kind = Cost.KIND_UPFRONT,
                amountIndex = cost.amount.toIndex(),
                resourceIndex = cost.resource.toIndex(),
            )
            is ModelCost.Ongoing -> Cost(
                kind = Cost.KIND_ONGOING,
                amountIndex = cost.amount.toIndex(),
                resourceIndex = cost.resource.toIndex(),
            )
            is ModelCost.None -> error("Cost.None should never be encoded")
        }
    }

    private fun costToModel(wire: Cost): ModelCost {
        val amount = amountFromIndex(wire.amountIndex)
        val resource = resourceFromIndex(wire.resourceIndex)
        return when (wire.kind) {
            Cost.KIND_UPFRONT -> ModelCost.Upfront(amount = amount, resource = resource)
            Cost.KIND_ONGOING -> ModelCost.Ongoing(amount = amount, resource = resource)
            else -> error("Unknown cost kind: ${wire.kind}")
        }
    }

    /**
     * Parse the wire's cooldown string back to a `kotlin.time.Duration`.
     *
     * The contribute-form already has its own cooldown parser (in the
     * ability-listing-contributions module) that handles the units the user
     * is likely to type. We mirror its conventions here so a round-trip
     * through the wire preserves the user's input. For now we lean on
     * `Duration.parseOrNull` for the canonical Kotlin-formatted strings
     * (e.g., "30s", "5m"); free-form like "30 seconds" is rejected as
     * `Duration.ZERO`. Future refinement: share the cooldown parser
     * between the contribute form and this mapper.
     */
    private fun parseCooldown(text: String): Duration {
        if (text.isEmpty()) return Duration.ZERO
        return Duration.parseOrNull(text) ?: Duration.ZERO
    }

    // -------------------------------------------------------------------------
    // Status effect
    // -------------------------------------------------------------------------

    fun toWire(effect: ModelStatusEffect): StatusEffect = StatusEffect(
        name = effect.name,
        typeIndex = effect.type.toIndex(),
        propertyIndices = effect.properties.map { it.toIndex() },
        stackable = effect.stackable,
        description = effect.description,
    )

    fun toModel(wire: StatusEffect): ModelStatusEffect {
        return try {
            ModelStatusEffect(
                name = wire.name,
                type = statusTypeFromIndex(wire.typeIndex),
                properties = wire.propertyIndices.map { propertyFromIndex(it) },
                stackable = wire.stackable,
                description = wire.description,
            )
        } catch (e: IllegalArgumentException) {
            throw WireDecodeException("Failed to decode status effect '${wire.name}': ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // CharacterBuild
    // -------------------------------------------------------------------------

    private val slotConstructors: List<(AbsorbedEssence?) -> Attribute> = listOf(
        { Attribute.Power(essence = it) },
        { Attribute.Speed(essence = it) },
        { Attribute.Spirit(essence = it) },
        { Attribute.Recovery(essence = it) },
    )

    fun toWire(build: ModelCharacterBuild): Build {
        val slotAttrs = listOf(build.Power, build.Speed, build.Spirit, build.Recovery)
        val wireAttrs = slotAttrs.map { attr ->
            val absorbed = attr.essence
            if (absorbed == null) {
                BuildAttribute()
            } else {
                BuildAttribute(
                    essenceName = absorbed.essence.name,
                    confluence = absorbed.essence is Essence.Confluence,
                    abilities = absorbed.abilities.map { acquired ->
                        BuildAbility(
                            name = acquired.name,
                            rankIndex = acquired.rank.toIndex(),
                            tier = acquired.tier,
                            progress = acquired.progress,
                        )
                    },
                )
            }
        }
        return Build(
            name = build.name,
            race = build.race,
            racials = build.racialAbilities.map { it.name },
            attributes = wireAttrs,
        )
    }

    /**
     * Decode a wire build, looking up name references via the provided
     * callbacks. Unresolved references are dropped (empty slot if essence
     * missing; ability omitted if listing missing). The caller is responsible
     * for surfacing missing-reference info to the user via the preview layer.
     */
    fun toModel(
        wire: Build,
        manifestationLookup: (name: String) -> Essence.Manifestation?,
        confluenceLookup: (name: String) -> Essence.Confluence?,
        listingLookup: (name: String) -> Ability.Listing?,
    ): ModelCharacterBuild {
        require(wire.attributes.size == SlotIndex.SLOT_COUNT) {
            "Build '${wire.name}' has ${wire.attributes.size} attribute entries, expected ${SlotIndex.SLOT_COUNT}"
        }
        val racials = wire.racials.mapNotNull { listingLookup(it) }
        val attrs = wire.attributes.mapIndexed { index, wireAttr ->
            val absorbed = resolveAbsorbed(wireAttr, manifestationLookup, confluenceLookup, listingLookup)
            slotConstructors[index](absorbed)
        }.toSet()
        return ModelCharacterBuild(
            name = wire.name,
            race = wire.race,
            racialAbilities = racials,
            attributes = attrs,
        )
    }

    private fun resolveAbsorbed(
        wireAttr: BuildAttribute,
        manifestationLookup: (name: String) -> Essence.Manifestation?,
        confluenceLookup: (name: String) -> Essence.Confluence?,
        listingLookup: (name: String) -> Ability.Listing?,
    ): AbsorbedEssence? {
        if (wireAttr.essenceName.isEmpty()) return null
        val essence: Essence =
            if (wireAttr.confluence) confluenceLookup(wireAttr.essenceName) ?: return null
            else manifestationLookup(wireAttr.essenceName) ?: return null
        val acquired = wireAttr.abilities.mapNotNull { wireAbility ->
            val listing = listingLookup(wireAbility.name) ?: return@mapNotNull null
            listing.acquire(essence).copy(
                rank = rankFromIndex(wireAbility.rankIndex),
                tier = wireAbility.tier,
                progress = wireAbility.progress,
            )
        }
        return AbsorbedEssence(essence = essence, abilities = acquired)
    }
}
