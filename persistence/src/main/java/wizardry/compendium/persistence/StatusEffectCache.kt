package wizardry.compendium.persistence

import wizardry.compendium.domain.model.StatusEffect

interface StatusEffectCache {
    var contents: List<StatusEffect>
}
