package wizardry.compendium.persistence

import wizardry.compendium.domain.model.StatusEffect

data class IdentifiedStatusEffect(val id: Long, val statusEffect: StatusEffect)

interface StatusEffectCache {
    val identified: List<IdentifiedStatusEffect>

    val contents: List<StatusEffect>
        get() = identified.map { it.statusEffect }

    fun insert(statusEffect: StatusEffect): Long
    fun update(id: Long, statusEffect: StatusEffect)
    fun deleteById(id: Long)
    fun findIdByName(name: String): Long?
    fun replaceAll(statusEffects: List<StatusEffect>)
}
