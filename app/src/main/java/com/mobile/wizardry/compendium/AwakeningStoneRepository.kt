package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.AwakeningStoneProvider
import com.mobile.wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import com.mobile.wizardry.compendium.essences.model.AwakeningStone
import com.mobile.wizardry.compendium.persistence.AwakeningStoneCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AwakeningStoneRepository
@Inject constructor(
    private val dataLoader: AwakeningStoneDataLoader,
    private val cache: AwakeningStoneCache,
) : AwakeningStoneProvider {
    override suspend fun getAwakeningStones(): List<AwakeningStone> {
        return cache.contents.takeIf { it.isNotEmpty() }
            ?: dataLoader.loadAwakeningStoneData()
                .also { cache.contents = it }
    }
}
