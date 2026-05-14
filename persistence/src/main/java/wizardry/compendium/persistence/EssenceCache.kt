package wizardry.compendium.persistence

import wizardry.compendium.domain.model.Essence

interface EssenceCache {
    var contents: List<Essence>
}
