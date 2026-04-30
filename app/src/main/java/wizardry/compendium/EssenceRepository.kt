package wizardry.compendium

import wizardry.compendium.essences.EssenceProvider
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.persistence.EssenceCache
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
