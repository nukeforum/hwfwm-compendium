package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.essences.model.Essence

class EssenceFileCacheProvider(
    private val essenceDataLoader: EssenceDataLoader
) : EssenceProvider {
    private var essences: List<Essence>? = null

    override suspend fun getEssences(): List<Essence> {
        return essences
            ?: essenceDataLoader.loadEssenceData()
    }
}
