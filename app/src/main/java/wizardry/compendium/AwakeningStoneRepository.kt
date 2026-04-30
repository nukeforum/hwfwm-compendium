package wizardry.compendium

import wizardry.compendium.essences.AwakeningStoneProvider
import wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.persistence.AwakeningStoneCache
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
