package com.mobile.wizardry.compendium.essences.dataloader

import com.mobile.wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneDataLoader {
    suspend fun loadAwakeningStoneData(): List<AwakeningStone>
}
