package wizardry.compendium.persistence

import wizardry.compendium.domain.model.AwakeningStone

interface AwakeningStoneCache {
    var contents: List<AwakeningStone>
}
