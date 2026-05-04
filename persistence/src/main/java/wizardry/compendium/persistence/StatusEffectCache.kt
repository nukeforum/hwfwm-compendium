package wizardry.compendium.persistence

import wizardry.compendium.essences.model.StatusEffect

interface StatusEffectCache {
    var contents: List<StatusEffect>
}
