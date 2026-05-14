package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity

class EssenceDatabaseTest {

    private fun newDatabase(): EssenceDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return EssenceDatabase(driver)
    }

    @Test
    fun `manifestation round trips`() {
        val db = newDatabase()
        val fire = Essence.of(
            name = "Fire",
            description = "burns things",
            rarity = Rarity.Common,
            restricted = false,
        )

        db.writeAll(listOf(fire))

        assertEquals(listOf(fire), db.readAll())
    }

    @Test
    fun `restricted manifestation round trips`() {
        val db = newDatabase()
        val doom = Essence.of(
            name = "Doom",
            description = "forbidden power",
            rarity = Rarity.Legendary,
            restricted = true,
        )

        db.writeAll(listOf(doom))

        assertEquals(listOf(doom), db.readAll())
    }

    @Test
    fun `confluence with one set round trips`() {
        val db = newDatabase()
        val fire = Essence.of("Fire", "burns", Rarity.Common, false)
        val water = Essence.of("Water", "flows", Rarity.Common, false)
        val earth = Essence.of("Earth", "stable", Rarity.Common, false)
        val steam = Essence.of(
            name = "Steam",
            restricted = false,
            ConfluenceSet(setOf(fire, water, earth)),
        )

        db.writeAll(listOf(fire, water, earth, steam))

        val readBack = db.readAll()
        assertEquals(listOf(earth, fire, steam, water), readBack)
        val readConfluence = readBack.filterIsInstance<Essence.Confluence>().single()
        assertEquals(steam, readConfluence)
    }

    @Test
    fun `confluence with multiple sets round trips`() {
        val db = newDatabase()
        val fire = Essence.of("Fire", "burns", Rarity.Common, false)
        val water = Essence.of("Water", "flows", Rarity.Common, false)
        val earth = Essence.of("Earth", "stable", Rarity.Common, false)
        val air = Essence.of("Air", "blows", Rarity.Common, false)
        val multi = Essence.of(
            name = "Multi",
            restricted = false,
            ConfluenceSet(setOf(fire, water, earth)),
            ConfluenceSet(setOf(fire, water, air)),
        )

        db.writeAll(listOf(fire, water, earth, air, multi))

        val readConfluence = db.readAll().filterIsInstance<Essence.Confluence>().single()
        assertEquals(multi, readConfluence)
        assertEquals(2, readConfluence.confluenceSets.size)
    }

    @Test
    fun `restricted flags on confluence and individual sets round trip`() {
        val db = newDatabase()
        val fire = Essence.of("Fire", "burns", Rarity.Common, false)
        val water = Essence.of("Water", "flows", Rarity.Common, false)
        val earth = Essence.of("Earth", "stable", Rarity.Common, false)
        val secret = Essence.of(
            name = "Secret",
            restricted = true,
            ConfluenceSet(setOf(fire, water, earth), isRestricted = true),
        )

        db.writeAll(listOf(fire, water, earth, secret))

        val readConfluence = db.readAll().filterIsInstance<Essence.Confluence>().single()
        assertEquals(true, readConfluence.isRestricted)
        assertEquals(true, readConfluence.confluenceSets.single().isRestricted)
    }

    @Test
    fun `result is sorted by name interleaving manifestations and confluences`() {
        val db = newDatabase()
        val apple = Essence.of("Apple", "tart", Rarity.Common, false)
        val beta = Essence.of("Beta", "second", Rarity.Common, false)
        val charlie = Essence.of("Charlie", "third", Rarity.Common, false)
        val zebra = Essence.of("Zebra", "striped", Rarity.Common, false)
        val aardvark = Essence.of(
            name = "Aardvark",
            restricted = false,
            ConfluenceSet(setOf(apple, beta, charlie)),
        )

        db.writeAll(listOf(apple, beta, charlie, zebra, aardvark))

        assertEquals(
            listOf("Aardvark", "Apple", "Beta", "Charlie", "Zebra"),
            db.readAll().map { it.name },
        )
    }

    @Test
    fun `writeAll replaces previous contents`() {
        val db = newDatabase()
        val first = Essence.of("First", "one", Rarity.Common, false)
        val second = Essence.of("Second", "two", Rarity.Rare, false)

        db.writeAll(listOf(first))
        db.writeAll(listOf(second))

        assertEquals(listOf(second), db.readAll())
    }
}
