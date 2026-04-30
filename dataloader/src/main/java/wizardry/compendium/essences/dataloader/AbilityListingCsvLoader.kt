package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

class AbilityListingCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : AbilityListingDataLoader {
    override suspend fun loadAbilityListingData(): List<Ability.Listing> = withContext(Dispatchers.IO) {
        runCatching {
            source.getInputStreamFor(ABILITY_LISTING_FILE_NAME).use { stream ->
                stream.reader().readLines()
                    .filter { it.isNotBlank() }
                    .map { entry -> Ability.Listing.of(name = entry.substringBefore(',').trim()) }
                    .sortedBy { it.name }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val ABILITY_LISTING_FILE_NAME = "ability_listings.csv"
    }
}
