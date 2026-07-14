package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.RaceTemplate
import javax.inject.Inject

/**
 * Reads `assets/races.csv`, one row per canonical race:
 *
 * `RaceName,Ability1,Ability2,Ability3,Ability4,Ability5,Ability6`
 *
 * Each ability column is the NAME of a canonical ability listing — races
 * reference shared, first-class racial abilities rather than embedding their
 * own copies. Every name in this file must have a matching row in
 * `ability_listings.csv` (see [AbilityListingCsvLoader]); the repository
 * layer stores the reference as a `canon:<name>` tagged ref and resolves it
 * against the canonical listing cache at read time, dropping refs it cannot
 * resolve.
 *
 * Exactly [RaceTemplate.RACIAL_ABILITY_COUNT] ability columns are required —
 * a canonical race that cannot name six fixed racial abilities (e.g.
 * Outworlder, whose five non-Astral abilities vary per individual) does not
 * belong in this file.
 */
class RaceTemplateCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : RaceTemplateDataLoader {
    override suspend fun loadRaceTemplateData(): List<RaceTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            source.getInputStreamFor(RACES_FILE_NAME).use { stream ->
                stream.reader().readLines()
                    .filter { it.isNotBlank() }
                    .map(::parseRow)
                    .sortedBy { it.name }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRow(row: String): RaceTemplate {
        val cols = row.split(",")
        require(cols.size == 1 + RaceTemplate.RACIAL_ABILITY_COUNT) {
            "Malformed races.csv row (expected ${1 + RaceTemplate.RACIAL_ABILITY_COUNT} cols): $row"
        }
        return RaceTemplate(
            name = cols[0].trim(),
            racialAbilities = cols.drop(1).map { Ability.Listing.of(name = it.trim()) },
        )
    }

    companion object {
        private const val RACES_FILE_NAME = "races.csv"
    }
}
