package wizardry.compendium

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.essences.dataloader.AbilityListingCsvLoader
import wizardry.compendium.essences.dataloader.FileStreamSource
import wizardry.compendium.essences.dataloader.RaceTemplateCsvLoader
import java.io.File
import java.io.InputStream

/**
 * Guards the coupling between the two canonical seed assets: every ability
 * name a race row references in `races.csv` must have a matching listing row
 * in `ability_listings.csv`, or the repository's `canon:<name>` ref resolution
 * silently drops that ability and the seeded race surfaces with fewer than
 * six racial abilities.
 *
 * Also feeds the REAL shipped assets through the REAL csv loaders: the loaders
 * collapse the whole load to empty on any unparseable row (bad column count,
 * unknown [AbilityType] token), so asserting the exact seeded counts here is
 * what catches a CSV edit that would otherwise silently wipe the canonical
 * seed at runtime.
 */
class CanonicalRaceSeedDataTest {

    private fun assetFile(name: String): File {
        // AGP unit tests run with the module directory as the working dir,
        // but tolerate a repo-root working dir too.
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")
    }

    private fun asset(name: String): List<String> =
        assetFile(name).readLines().filter { it.isNotBlank() }

    private val assetSource = object : FileStreamSource {
        override fun getInputStreamFor(filename: String): InputStream =
            assetFile(filename).inputStream()
    }

    @Test
    fun `every races-csv ability name has a matching ability_listings-csv row`() {
        val listingNames = asset("ability_listings.csv")
            .map { it.split(",", limit = 3)[0].trim() }
            .toSet()

        val unresolved = asset("races.csv")
            .flatMap { row ->
                val cols = row.split(",")
                cols.drop(1).map { ability -> cols[0] to ability.trim() }
            }
            .filterNot { (_, ability) -> ability in listingNames }

        assertTrue("Unresolvable canonical ability refs: $unresolved", unresolved.isEmpty())
    }

    @Test
    fun `every race row names exactly six racial abilities`() {
        asset("races.csv").forEach { row ->
            assertEquals("Malformed races.csv row: $row", 7, row.split(",").size)
        }
    }

    @Test
    fun `the seven canonical races are seeded`() {
        assertEquals(
            listOf("Celestine", "Draconian", "Elf", "Human", "Leonid", "Runic", "Smoulder"),
            asset("races.csv").map { it.substringBefore(',') }.sorted(),
        )
    }

    @Test
    fun `canonical listing names are unique ignoring case`() {
        val names = asset("ability_listings.csv").map { it.split(",", limit = 3)[0].trim().lowercase() }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `the real ability_listings-csv loads all 39 listings through the real loader`() = runBlocking {
        val listings = AbilityListingCsvLoader(assetSource).loadAbilityListingData()
        assertEquals(39, listings.size)
    }

    @Test
    fun `the real races-csv loads all 7 races through the real loader`() = runBlocking {
        val races = RaceTemplateCsvLoader(assetSource).loadRaceTemplateData()
        assertEquals(7, races.size)
    }

    @Test
    fun `every listing a race references classifies as racial in the character build pickers`() = runBlocking {
        val listingsByName = AbilityListingCsvLoader(assetSource).loadAbilityListingData()
            .associateBy { it.name }
        val races = RaceTemplateCsvLoader(assetSource).loadRaceTemplateData()

        val nonRacial = races.flatMap { race ->
            race.racialAbilities.mapNotNull { ability ->
                val listing = listingsByName[ability.name]
                val racial = listing != null &&
                    listing.effects.isNotEmpty() &&
                    listing.effects.all { it.type == AbilityType.RacialAbility }
                if (racial) null else race.name to ability.name
            }
        }

        assertTrue(
            "Race-referenced listings without a RacialAbility-typed effect (these leak into the essence-slot picker): $nonRacial",
            nonRacial.isEmpty(),
        )
    }
}
