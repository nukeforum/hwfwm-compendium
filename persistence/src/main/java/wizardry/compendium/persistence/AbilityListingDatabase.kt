package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Resource
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class AbilityListingDatabase @Inject constructor(driver: SqlDriver) : AbilityListingCache {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.abilityListingsQueries

    override val identified: List<IdentifiedListing>
        get() {
            val effectsByListingId = q.selectAllAbilityEffects().executeAsList()
                .groupBy { it.listing_id }
            val propertiesByEffect = q.selectAllEffectProperties().executeAsList()
                .groupBy { it.effect_id }
            val costsByEffect = q.selectAllEffectCosts().executeAsList()
                .groupBy { it.effect_id }

            return q.selectAllAbilityListings().executeAsList()
                .map { row ->
                    val effects = effectsByListingId[row.id].orEmpty().map { effectRow ->
                        Effect.AbilityEffect(
                            rank = Rank.valueOf(effectRow.rank),
                            type = lookupAbilityType(effectRow.type),
                            properties = propertiesByEffect[effectRow.id].orEmpty()
                                .map { lookupProperty(it.property_) },
                            cost = costsByEffect[effectRow.id].orEmpty()
                                .map { it.toCost() },
                            cooldown = effectRow.cooldown_seconds.seconds,
                            description = effectRow.description,
                            replacementKey = effectRow.replacement_key,
                        )
                    }
                    IdentifiedListing(row.id, Ability.Listing(name = row.name, effects = effects))
                }
                .sortedBy { it.listing.name }
        }

    override fun insert(listing: Ability.Listing): Long = db.transactionWithResult {
        q.insertAbilityListing(name = listing.name)
        val id = q.lastInsertRowId().executeAsOne()
        writeEffects(listing.effects, listingId = id)
        id
    }

    override fun update(id: Long, listing: Ability.Listing) = db.transaction {
        q.updateAbilityListingName(name = listing.name, id = id)
        val effectIds = q.selectEffectIdsForListing(listing_id = id).executeAsList()
        effectIds.forEach { effectId ->
            q.deleteEffectPropertiesForEffect(effect_id = effectId)
            q.deleteEffectCostsForEffect(effect_id = effectId)
        }
        q.deleteEffectsForListing(listing_id = id)
        writeEffects(listing.effects, listingId = id)
    }

    override fun deleteById(id: Long) = db.transaction {
        val effectIds = q.selectEffectIdsForListing(listing_id = id).executeAsList()
        effectIds.forEach { effectId ->
            q.deleteEffectPropertiesForEffect(effect_id = effectId)
            q.deleteEffectCostsForEffect(effect_id = effectId)
        }
        q.deleteEffectsForListing(listing_id = id)
        q.deleteAbilityListingById(id = id)
    }

    override fun findIdByName(name: String): Long? =
        q.selectAbilityListingId(name = name).executeAsOneOrNull()

    override fun replaceAll(listings: List<Ability.Listing>) = db.transaction {
        q.deleteAllEffectCosts()
        q.deleteAllEffectProperties()
        q.deleteAllAbilityEffects()
        q.deleteAllAbilityListings()
        listings.forEach { listing ->
            q.insertAbilityListing(name = listing.name)
            val id = q.lastInsertRowId().executeAsOne()
            writeEffects(listing.effects, listingId = id)
        }
    }

    override fun selectEffectsWithStatusTokens(): List<Pair<Long, String>> =
        q.selectEffectsWithStatusTokens().executeAsList().map { it.id to it.description }

    override fun updateEffectDescription(effectId: Long, description: String) {
        q.updateEffectDescription(description = description, id = effectId)
    }

    private fun writeEffects(effects: List<Effect.AbilityEffect>, listingId: Long) {
        effects.forEachIndexed { effectIndex, effect ->
            q.insertAbilityEffect(
                listing_id = listingId,
                rank = effect.rank.name,
                type = effect.type.serialName(),
                cooldown_seconds = effect.cooldown.inWholeSeconds,
                description = effect.description,
                replacement_key = effect.replacementKey,
                ordinal = effectIndex.toLong(),
            )
            val effectId = q.lastInsertRowId().executeAsOne()
            effect.properties.forEachIndexed { i, property ->
                q.insertEffectProperty(effect_id = effectId, property_ = property.serialName(), ordinal = i.toLong())
            }
            effect.cost.forEachIndexed { i, cost ->
                val (kind, amount, resource) = cost.serialize()
                q.insertEffectCost(effect_id = effectId, kind = kind, amount = amount, resource = resource, ordinal = i.toLong())
            }
        }
    }
}

private fun Any.serialName(): String = this::class.simpleName!!

private fun Cost.serialize(): Triple<String, String, String> = when (this) {
    is Cost.None -> Triple("None", Amount.None.serialName(), Resource.Mana.serialName())
    is Cost.Upfront -> Triple("Upfront", amount.serialName(), resource.serialName())
    is Cost.Ongoing -> Triple("Ongoing", amount.serialName(), resource.serialName())
}

private fun wizardry.compendium.persistence.Effect_cost.toCost(): Cost = when (kind) {
    "None" -> Cost.None
    "Upfront" -> Cost.Upfront(lookupAmount(amount), lookupResource(resource))
    "Ongoing" -> Cost.Ongoing(lookupAmount(amount), lookupResource(resource))
    else -> error("Unknown cost kind: $kind")
}

private val abilityTypesByName: Map<String, AbilityType> by lazy { sealedObjectsByName() }
private val propertiesByName: Map<String, Property> by lazy { sealedObjectsByName() }
private val amountsByName: Map<String, Amount> by lazy { sealedObjectsByName() }
private val resourcesByName: Map<String, Resource> by lazy { sealedObjectsByName() }

private fun lookupAbilityType(name: String): AbilityType =
    abilityTypesByName[name] ?: error("Unknown AbilityType: $name")

private fun lookupProperty(name: String): Property =
    propertiesByName[name] ?: error("Unknown Property: $name")

private fun lookupAmount(name: String): Amount =
    amountsByName[name] ?: error("Unknown Amount: $name")

private fun lookupResource(name: String): Resource =
    resourcesByName[name] ?: error("Unknown Resource: $name")

private inline fun <reified T : Any> sealedObjectsByName(): Map<String, T> {
    val klass: KClass<T> = T::class
    return klass.sealedSubclasses
        .mapNotNull { sub -> sub.objectInstance?.let { sub.simpleName!! to it } }
        .toMap()
}
