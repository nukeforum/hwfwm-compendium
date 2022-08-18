package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.dataloader.EssenceDataLoader
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.persistence.EssenceCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EssenceRepository
@Inject constructor(
    private val essenceDataLoader: EssenceDataLoader,
    private val cache: EssenceCache,
) : EssenceProvider {
    override suspend fun getEssences(): List<Essence> {
        return cache.contents.takeIf { it.isNotEmpty() }
            ?: essenceDataLoader.loadEssenceData()
                .also { cache.contents = it }
    }
}
