package wizardry.compendium.essences.dataloader

import wizardry.compendium.domain.model.AwakeningStone

interface AwakeningStoneDataLoader {
    suspend fun loadAwakeningStoneData(): List<AwakeningStone>
}
