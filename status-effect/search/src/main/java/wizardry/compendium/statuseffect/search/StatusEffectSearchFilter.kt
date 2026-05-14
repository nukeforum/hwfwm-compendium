package wizardry.compendium.statuseffect.search

import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

/**
 * Either an Affliction-bucket filter or a Boon-bucket filter, optionally
 * narrowed to a set of subtypes. An empty `subtypes` set means "all subtypes
 * of this top level".
 *
 * Absence of a filter (the view-model holds none of these) means "no type
 * constraint".
 */
sealed interface StatusEffectSearchFilter {
    fun predicate(effect: StatusEffect): Boolean

    data class Affliction(val subtypes: Set<StatusType.Affliction>) : StatusEffectSearchFilter {
        override fun predicate(effect: StatusEffect): Boolean {
            if (effect.type !is StatusType.Affliction) return false
            return subtypes.isEmpty() || effect.type in subtypes
        }
    }

    data class Boon(val subtypes: Set<StatusType.Boon>) : StatusEffectSearchFilter {
        override fun predicate(effect: StatusEffect): Boolean {
            if (effect.type !is StatusType.Boon) return false
            return subtypes.isEmpty() || effect.type in subtypes
        }
    }
}
