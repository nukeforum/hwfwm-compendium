package com.mobile.wizardry.compendium.essences

import com.mobile.wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneProvider {
    suspend fun getAwakeningStones(): List<AwakeningStone>
}
