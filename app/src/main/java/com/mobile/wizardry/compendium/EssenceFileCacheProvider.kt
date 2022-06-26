package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.data.Essence
import com.mobile.wizardry.compendium.dataloader.EssenceDataLoader

class EssenceFileCacheProvider(
    private val essenceDataLoader: EssenceDataLoader
) : EssenceProvider {
    private var essences: List<Essence>? = null

    override suspend fun getEssences(): List<Essence> {
        return essences
            ?: essenceDataLoader.loadEssenceData()
    }
}
