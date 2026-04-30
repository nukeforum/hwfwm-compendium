package wizardry.compendium.persistence

import wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneCache {
    var contents: List<AwakeningStone>
}
