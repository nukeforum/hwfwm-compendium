package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity

class AwakeningStoneDatabaseTest {

    private fun newDatabase(): AwakeningStoneDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AwakeningStoneDatabase(driver)
    }

    @Test
    fun `insert assigns positive id and identified reads it back`() {
        val db = newDatabase()
        val stone = AwakeningStone.of("Ruby", Rarity.Rare)
        val id = db.insert(stone)
        assertTrue("id positive", id > 0)
        assertEquals(listOf(IdentifiedAwakeningStone(id, stone)), db.identified)
    }

    @Test
    fun `consecutive inserts allocate distinct ids`() {
        val db = newDatabase()
        val id1 = db.insert(AwakeningStone.of("A", Rarity.Common))
        val id2 = db.insert(AwakeningStone.of("B", Rarity.Common))
        assertNotEquals(id1, id2)
    }

    @Test
    fun `update preserves id across rename`() {
        val db = newDatabase()
        val id = db.insert(AwakeningStone.of("Ruby", Rarity.Rare))
        db.update(id, AwakeningStone.of("Emerald", Rarity.Rare))
        assertEquals(id, db.findIdByName("Emerald"))
        assertNull(db.findIdByName("Ruby"))
    }

    @Test
    fun `deleteById removes the row`() {
        val db = newDatabase()
        val id = db.insert(AwakeningStone.of("Ruby", Rarity.Rare))
        db.deleteById(id)
        assertEquals(emptyList<IdentifiedAwakeningStone>(), db.identified)
    }

    @Test
    fun `findIdByName returns null when absent`() {
        val db = newDatabase()
        assertNull(db.findIdByName("Nope"))
    }

    @Test
    fun `replaceAll wipes existing rows and re-inserts`() {
        val db = newDatabase()
        val firstId = db.insert(AwakeningStone.of("First", Rarity.Common))

        val replacement = AwakeningStone.of("Replacement", Rarity.Epic)
        db.replaceAll(listOf(replacement))

        assertNull(db.findIdByName("First"))
        val identified = db.identified
        assertEquals(1, identified.size)
        assertEquals(replacement, identified.single().stone)
        assertNotEquals(firstId, identified.single().id)
    }

    @Test
    fun `identified is sorted by name`() {
        val db = newDatabase()
        db.insert(AwakeningStone.of("Zebra", Rarity.Common))
        db.insert(AwakeningStone.of("Apple", Rarity.Rare))
        db.insert(AwakeningStone.of("Mango", Rarity.Epic))

        val names = db.identified.map { it.stone.name }
        assertEquals(listOf("Apple", "Mango", "Zebra"), names)
    }
}
