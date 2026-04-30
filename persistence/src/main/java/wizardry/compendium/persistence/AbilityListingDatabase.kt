package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Resource
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AbilityListingDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.abilityListingsQueries

    fun writeAll(listings: List<Ability.Listing>) {
        db.transaction {
            q.deleteAllEffectCosts()
            q.deleteAllEffectProperties()
            q.deleteAllAbilityEffects()
            q.deleteAllAbilityListings()

            listings.forEach { listing ->
                q.insertAbilityListing(name = listing.name)
                listing.effects.forEachIndexed { effectIndex, effect ->
                    q.insertAbilityEffect(
                        listing_name = listing.name,
                        rank = effect.rank.name,
                        type = effect.type.serialName(),
                        cooldown_seconds = effect.cooldown.inWholeSeconds,
                        description = effect.description,
                        replacement_key = effect.replacementKey,
                        ordinal = effectIndex.toLong(),
                    )
                    val effectId = q.lastInsertRowId().executeAsOne()
                    effect.properties.forEachIndexed { i, property ->
                        q.insertEffectProperty(
                            effect_id = effectId,
                            property_ = property.serialName(),
                            ordinal = i.toLong(),
                        )
                    }
                    effect.cost.forEachIndexed { i, cost ->
                        val (kind, amount, resource) = cost.serialize()
                        q.insertEffectCost(
                            effect_id = effectId,
                            kind = kind,
                            amount = amount,
                            resource = resource,
                            ordinal = i.toLong(),
                        )
                    }
                }
            }
        }
    }

    fun readAll(): List<Ability.Listing> {
        val effectsByListing = q.selectAllAbilityEffects().executeAsList()
            .groupBy { it.listing_name }
        val propertiesByEffect = q.selectAllEffectProperties().executeAsList()
            .groupBy { it.effect_id }
        val costsByEffect = q.selectAllEffectCosts().executeAsList()
            .groupBy { it.effect_id }

        return q.selectAllAbilityListings().executeAsList()
            .map { name ->
                val effects = effectsByListing[name].orEmpty().map { row ->
                    Effect.AbilityEffect(
                        rank = Rank.valueOf(row.rank),
                        type = lookupAbilityType(row.type),
                        properties = propertiesByEffect[row.id].orEmpty()
                            .map { lookupProperty(it.property_) },
                        cost = costsByEffect[row.id].orEmpty()
                            .map { it.toCost() },
                        cooldown = row.cooldown_seconds.seconds,
                        description = row.description,
                        replacementKey = row.replacement_key,
                    )
                }
                Ability.Listing(name = name, effects = effects)
            }
            .sortedBy { it.name }
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
