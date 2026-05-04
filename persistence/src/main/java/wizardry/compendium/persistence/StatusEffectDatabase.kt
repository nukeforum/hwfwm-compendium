package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import javax.inject.Inject

class StatusEffectDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)

    fun writeAll(effects: List<StatusEffect>) {
        db.transaction {
            db.statusEffectsQueries.deleteAllStatusEffects()
            effects.forEach { effect ->
                db.statusEffectsQueries.insertStatusEffect(
                    name = effect.name,
                    type = encodeType(effect.type),
                    stackable = if (effect.stackable) 1L else 0L,
                    description = effect.description,
                    properties = effect.properties.joinToString(separator = "|") { it.toString() },
                )
            }
        }
    }

    fun readAll(): List<StatusEffect> {
        return db.statusEffectsQueries.selectAllStatusEffects().executeAsList()
            .map { row ->
                StatusEffect(
                    name = row.name,
                    type = decodeType(row.type),
                    properties = decodeProperties(row.properties),
                    stackable = row.stackable != 0L,
                    description = row.description,
                )
            }
            .sortedBy { it.name }
    }

    private fun decodeProperties(serialized: String): List<Property> {
        if (serialized.isEmpty()) return emptyList()
        return serialized.split("|").mapNotNull { token -> propertyByToken[token] }
    }

    companion object {
        private val allProperties: List<Property> = listOf(
            Property.Affliction, Property.Blood, Property.Boon, Property.Channel, Property.Cleanse,
            Property.Combination, Property.Conjuration, Property.Consumable, Property.CounterExecute,
            Property.Curse, Property.DamageOverTime, Property.Dark, Property.Darkness,
            Property.Dimension, Property.Disease, Property.Drain, Property.Elemental, Property.Essence,
            Property.Execute, Property.Fire, Property.HealOverTime, Property.Healing, Property.Holy,
            Property.Ice, Property.Illusion, Property.Light, Property.Lightning, Property.Magic,
            Property.ManaOverTime, Property.Melee, Property.Momentum, Property.Movement,
            Property.Nature, Property.Perception, Property.Poison, Property.Recovery,
            Property.Restoration, Property.Retributive, Property.Ritual, Property.Sacrifice,
            Property.ShapeChange, Property.Signal, Property.Stacking, Property.StaminaOverTime,
            Property.Summon, Property.Teleport, Property.Tracking, Property.Trap, Property.Unholy,
            Property.Vehicle, Property.Wounding, Property.Zone,
        )
        private val propertyByToken: Map<String, Property> = allProperties.associateBy { it.toString() }

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
