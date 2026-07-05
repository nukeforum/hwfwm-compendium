package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.RaceTemplate

class RaceTemplateDatabaseTest {

    /** Resolver that encodes ALL refs as canonical form (canon:<name>). Sufficient for round-trip tests. */
    private object CanonOnlyResolver : RaceTemplateRefResolver {
        override fun encodeListing(listing: Ability.Listing): String = "canon:${listing.name}"
    }

    private fun newDatabase(): RaceTemplateDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return RaceTemplateDatabase(driver)
    }

    private fun template(name: String, racialNames: List<String>): RaceTemplate =
        RaceTemplate(name = name, racialAbilities = racialNames.map { Ability.Listing.of(it) })

    @Test
    fun `empty database returns empty raw lists`() {
        val db = newDatabase()
        assertEquals(emptyList<RawRaceTemplateRow>(), db.readAllRaceTemplates())
        assertEquals(emptyList<RawRaceTemplateAbilityRow>(), db.readAllRacialAbilities())
    }

    @Test
    fun `writeAll encodes refs via the resolver and round-trips raw rows in ordinal order`() {
        val db = newDatabase()
        db.writeAll(
            listOf(template("Golem", listOf("Stoneskin", "Tremor", "Endure"))),
            CanonOnlyResolver,
        )

        assertEquals(listOf(RawRaceTemplateRow("Golem")), db.readAllRaceTemplates())
        assertEquals(
            listOf(
                RawRaceTemplateAbilityRow("Golem", "canon:Stoneskin", 0L),
                RawRaceTemplateAbilityRow("Golem", "canon:Tremor", 1L),
                RawRaceTemplateAbilityRow("Golem", "canon:Endure", 2L),
            ),
            db.readAllRacialAbilities(),
        )
    }

    @Test
    fun `upsert replaces a single template without affecting others`() {
        val db = newDatabase()
        db.writeAll(
            listOf(
                template("Alpha", listOf("A1", "A2")),
                template("Beta", listOf("B1")),
            ),
            CanonOnlyResolver,
        )

        db.upsert(template("Alpha", listOf("NewTrait")), CanonOnlyResolver)

        val racials = db.readAllRacialAbilities().groupBy { it.templateName }
        assertEquals(listOf("canon:NewTrait"), racials.getValue("Alpha").map { it.listingRef })
        assertEquals(listOf("canon:B1"), racials.getValue("Beta").map { it.listingRef })
    }

    @Test
    fun `deleteByName removes the template and all dependent rows`() {
        val db = newDatabase()
        db.writeAll(
            listOf(
                template("Alpha", listOf("A1")),
                template("Beta", listOf("B1")),
            ),
            CanonOnlyResolver,
        )

        db.deleteByName("Alpha")

        assertEquals(listOf("Beta"), db.readAllRaceTemplates().map { it.name })
        assertTrue(db.readAllRacialAbilities().none { it.templateName == "Alpha" })
        assertEquals(
            listOf("canon:B1"),
            db.readAllRacialAbilities().filter { it.templateName == "Beta" }.map { it.listingRef },
        )
    }

    @Test
    fun `templatesReferencingListingRef finds templates that use a given ref`() {
        val db = newDatabase()
        db.writeAll(
            listOf(
                template("UsesShared", listOf("Shared")),
                template("AlsoUsesShared", listOf("Other", "Shared")),
                template("Unrelated", listOf("Nope")),
            ),
            CanonOnlyResolver,
        )

        assertEquals(
            listOf("AlsoUsesShared", "UsesShared"),
            db.templatesReferencingListingRef("canon:Shared"),
        )
    }

    @Test
    fun `writeAll wipes prior rows`() {
        val db = newDatabase()
        db.writeAll(listOf(template("Old", listOf("O1"))), CanonOnlyResolver)
        db.writeAll(listOf(template("New", listOf("N1"))), CanonOnlyResolver)
        assertEquals(listOf("New"), db.readAllRaceTemplates().map { it.name })
        assertEquals(listOf("canon:N1"), db.readAllRacialAbilities().map { it.listingRef })
    }

    @Test
    fun `resolver receiving Contributed encoding round-trips discriminated refs`() {
        val db = newDatabase()
        val contribResolver = object : RaceTemplateRefResolver {
            override fun encodeListing(listing: Ability.Listing): String = "contr:42"
        }
        db.writeAll(listOf(template("X", listOf("Custom"))), contribResolver)
        assertEquals(listOf("contr:42"), db.readAllRacialAbilities().map { it.listingRef })
    }
}
