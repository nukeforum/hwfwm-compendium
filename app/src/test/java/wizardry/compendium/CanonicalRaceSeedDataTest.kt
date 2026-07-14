package wizardry.compendium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the coupling between the two canonical seed assets: every ability
 * name a race row references in `races.csv` must have a matching listing row
 * in `ability_listings.csv`, or the repository's `canon:<name>` ref resolution
 * silently drops that ability and the seeded race surfaces with fewer than
 * six racial abilities.
 */
class CanonicalRaceSeedDataTest {

    private fun asset(name: String): List<String> {
        // AGP unit tests run with the module directory as the working dir,
        // but tolerate a repo-root working dir too.
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")
        return file.readLines().filter { it.isNotBlank() }
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
}
