package wizardry.compendium.persistence

import wizardry.compendium.essences.model.Essence

interface EssenceCache {
    var contents: List<Essence>
}
