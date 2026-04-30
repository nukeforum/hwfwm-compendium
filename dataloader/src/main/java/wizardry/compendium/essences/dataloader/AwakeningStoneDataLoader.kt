package wizardry.compendium.essences.dataloader

import wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneDataLoader {
    suspend fun loadAwakeningStoneData(): List<AwakeningStone>
}
