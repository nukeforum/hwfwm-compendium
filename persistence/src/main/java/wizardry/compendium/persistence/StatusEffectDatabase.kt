package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import javax.inject.Inject
import kotlin.reflect.KClass

class StatusEffectDatabase @Inject constructor(driver: SqlDriver) : StatusEffectCache {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.statusEffectsQueries

    override val identified: List<IdentifiedStatusEffect>
        get() = q.selectAllStatusEffects().executeAsList().map { row ->
            IdentifiedStatusEffect(
                id = row.id,
                statusEffect = StatusEffect(
                    name = row.name,
                    type = decodeType(row.type),
                    properties = decodeProperties(row.properties),
                    stackable = row.stackable != 0L,
                    description = row.description,
                ),
            )
        }.sortedBy { it.statusEffect.name }

    override fun insert(statusEffect: StatusEffect): Long = db.transactionWithResult {
        q.insertStatusEffect(
            name = statusEffect.name,
            type = encodeType(statusEffect.type),
            stackable = if (statusEffect.stackable) 1L else 0L,
            description = statusEffect.description,
            properties = statusEffect.properties.joinToString("|") { it.toString() },
        )
        q.lastInsertRowId().executeAsOne()
    }

    override fun update(id: Long, statusEffect: StatusEffect) {
        q.updateStatusEffectFully(
            name = statusEffect.name,
            type = encodeType(statusEffect.type),
            stackable = if (statusEffect.stackable) 1L else 0L,
            description = statusEffect.description,
            properties = statusEffect.properties.joinToString("|") { it.toString() },
            id = id,
        )
    }

    override fun deleteById(id: Long) {
        q.deleteStatusEffectById(id = id)
    }

    override fun findIdByName(name: String): Long? =
        q.selectStatusEffectId(name = name).executeAsOneOrNull()

    override fun replaceAll(statusEffects: List<StatusEffect>) = db.transaction {
        q.deleteAllStatusEffects()
        statusEffects.forEach { effect ->
            q.insertStatusEffect(
                name = effect.name,
                type = encodeType(effect.type),
                stackable = if (effect.stackable) 1L else 0L,
                description = effect.description,
                properties = effect.properties.joinToString("|") { it.toString() },
            )
        }
    }

    private fun decodeProperties(serialized: String): List<Property> {
        if (serialized.isEmpty()) return emptyList()
        return serialized.split("|").map { token ->
            propertyByToken[token] ?: error("Unknown Property: $token")
        }
    }

    companion object {
        private val propertyByToken: Map<String, Property> by lazy {
            val klass: KClass<Property> = Property::class
            klass.sealedSubclasses
                .mapNotNull { it.objectInstance }
                .associateBy { it.toString() }
        }

        fun encodeType(type: StatusType): String = when (type) {
            StatusType.Affliction.Curse -> "Affliction.Curse"
            StatusType.Affliction.Disease -> "Affliction.Disease"
            StatusType.Affliction.Elemental -> "Affliction.Elemental"
            StatusType.Affliction.Holy -> "Affliction.Holy"
            StatusType.Affliction.Magic -> "Affliction.Magic"
            StatusType.Affliction.Poison -> "Affliction.Poison"
            StatusType.Affliction.Unholy -> "Affliction.Unholy"
            StatusType.Affliction.Wound -> "Affliction.Wound"
            StatusType.Affliction.UnTyped -> "Affliction.UnTyped"
            StatusType.Boon.Holy -> "Boon.Holy"
            StatusType.Boon.Magic -> "Boon.Magic"
            StatusType.Boon.Unholy -> "Boon.Unholy"
            StatusType.Boon.UnTyped -> "Boon.UnTyped"
        }

        fun decodeType(token: String): StatusType = when (token) {
            "Affliction.Curse" -> StatusType.Affliction.Curse
            "Affliction.Disease" -> StatusType.Affliction.Disease
            "Affliction.Elemental" -> StatusType.Affliction.Elemental
            "Affliction.Holy" -> StatusType.Affliction.Holy
            "Affliction.Magic" -> StatusType.Affliction.Magic
            "Affliction.Poison" -> StatusType.Affliction.Poison
            "Affliction.Unholy" -> StatusType.Affliction.Unholy
            "Affliction.Wound" -> StatusType.Affliction.Wound
            "Affliction.UnTyped" -> StatusType.Affliction.UnTyped
            "Boon.Holy" -> StatusType.Boon.Holy
            "Boon.Magic" -> StatusType.Boon.Magic
            "Boon.Unholy" -> StatusType.Boon.Unholy
            "Boon.UnTyped" -> StatusType.Boon.UnTyped
            else -> error("Unknown StatusType token: $token")
        }
    }
}
