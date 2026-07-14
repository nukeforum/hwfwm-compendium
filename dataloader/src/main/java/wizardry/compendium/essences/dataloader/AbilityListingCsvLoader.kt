package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Rank
import javax.inject.Inject
import kotlin.time.Duration

/**
 * Reads `assets/ability_listings.csv`. Two row shapes are accepted:
 *
 * - `name` — a bare listing with no effect data, for canonical abilities whose
 *   details are unknown / not yet curated. Note a bare listing has no
 *   [AbilityType] classification, so prefer `name,type,???` when the type is
 *   known even if the source sheet's description is "???" — the character
 *   build editor's racial/slot pickers classify by effect type.
 * - `name,type,description` — a listing with a single effect of the given
 *   [AbilityType] (the token is the type's `toString()` form, e.g.
 *   `Racial ability`). The description is the LAST column and may itself
 *   contain commas — rows are split with `limit = 3`, mirroring how
 *   [StatusEffectCsvLoader] keeps free text out of the delimiter's way.
 *   Seeded effects carry [Rank.Unranked] (the domain's "no rank" value), no
 *   properties, and [Cost.None]: canonical sources don't record those details.
 *
 * The racial abilities referenced by canonical race templates (`races.csv`,
 * see [RaceTemplateCsvLoader]) are seeded here so they exist as first-class
 * canonical listings that races and character builds reference by name.
 */
class AbilityListingCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : AbilityListingDataLoader {
    override suspend fun loadAbilityListingData(): List<Ability.Listing> = withContext(Dispatchers.IO) {
        runCatching {
            source.getInputStreamFor(ABILITY_LISTING_FILE_NAME).use { stream ->
                stream.reader().readLines()
                    .filter { it.isNotBlank() }
                    .map(::parseRow)
                    .sortedBy { it.name }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRow(row: String): Ability.Listing {
        val cols = row.split(",", limit = 3)
        val listing = Ability.Listing.of(name = cols[0].trim())
        if (cols.size == 1) return listing
        require(cols.size == 3) { "Malformed ability_listings.csv row (expected 1 or 3 cols): $row" }
        val (_, typeToken, description) = cols
        return listing.copy(
            effects = listOf(
                Effect.AbilityEffect(
                    rank = Rank.Unranked,
                    type = decodeType(typeToken.trim()),
                    properties = emptyList(),
                    cost = listOf(Cost.None),
                    cooldown = Duration.ZERO,
                    description = description.trim(),
                ),
            ),
        )
    }

    private fun decodeType(token: String): AbilityType = when (token) {
        "Special attack" -> AbilityType.SpecialAttack
        "Special ability" -> AbilityType.SpecialAbility
        "Racial ability" -> AbilityType.RacialAbility
        "Spell" -> AbilityType.Spell
        "Aura" -> AbilityType.Aura
        "Conjuration" -> AbilityType.Conjuration
        "Familiar" -> AbilityType.Familiar
        "Summoning" -> AbilityType.Summoning
        else -> error("Unknown AbilityType token in ability_listings.csv: $token")
    }

    companion object {
        private const val ABILITY_LISTING_FILE_NAME = "ability_listings.csv"
    }
}
