package wizardry.compendium.essences

import wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneProvider {
    suspend fun getAwakeningStones(): List<AwakeningStone>
}
