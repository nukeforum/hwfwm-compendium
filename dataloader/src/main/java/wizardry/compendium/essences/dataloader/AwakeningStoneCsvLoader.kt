package wizardry.compendium.essences.dataloader

import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AwakeningStoneCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : AwakeningStoneDataLoader {
    override suspend fun loadAwakeningStoneData(): List<AwakeningStone> = withContext(Dispatchers.IO) {
        source.getInputStreamFor(AWAKENING_STONE_FILE_NAME).use { stream ->
            stream.reader().readLines()
                .filter { it.isNotBlank() }
                .map { entry ->
                    val (name, rarity) = entry.split(',')
                    AwakeningStone.of(name = name, rarity = Rarity.valueOf(rarity))
                }
                .sortedBy { it.name }
        }
    }

    companion object {
        private const val AWAKENING_STONE_FILE_NAME = "awakening_stones.csv"
    }
}
