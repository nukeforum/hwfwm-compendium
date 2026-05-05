package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity

class AwakeningStoneDatabaseTest {

    private fun newDatabase(): AwakeningStoneDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AwakeningStoneDatabase(driver)
    }

    @Test
    fun `round trip preserves name and rarity for every rarity`() {
        val db = newDatabase()
        val stones = Rarity.entries.map { rarity ->
            AwakeningStone.of(name = "Stone-${rarity.name}", rarity = rarity)
        }

        db.writeAll(stones)

        assertEquals(stones.sortedBy { it.name }, db.readAll())
    }

    @Test
    fun `result is sorted by name`() {
        val db = newDatabase()
        val zebra = AwakeningStone.of("Zebra", Rarity.Common)
        val apple = AwakeningStone.of("Apple", Rarity.Rare)
        val mango = AwakeningStone.of("Mango", Rarity.Epic)

        db.writeAll(listOf(zebra, apple, mango))

        assertEquals(listOf(apple, mango, zebra), db.readAll())
    }

    @Test
    fun `writeAll replaces previous contents`() {
        val db = newDatabase()
        val first = AwakeningStone.of("First", Rarity.Common)
        val second = AwakeningStone.of("Second", Rarity.Rare)

        db.writeAll(listOf(first))
        db.writeAll(listOf(second))

        assertEquals(listOf(second), db.readAll())
    }
}
