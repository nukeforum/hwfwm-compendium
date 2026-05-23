package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType

class StatusEffectDatabaseTest {

    private fun newDatabase(): StatusEffectDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return StatusEffectDatabase(driver)
    }

    private fun burn() = StatusEffect(
        name = "Burn",
        type = StatusType.Affliction.Elemental,
        properties = listOf(Property.Fire),
        stackable = true,
        description = "Inflicts fire damage over time.",
    )

    @Test
    fun `insert assigns positive id and identified reads it back`() {
        val db = newDatabase()
        val id = db.insert(burn())
        assertTrue("id positive", id > 0)
        assertEquals(listOf(IdentifiedStatusEffect(id, burn())), db.identified)
    }

    @Test
    fun `consecutive inserts allocate distinct ids`() {
        val db = newDatabase()
        val id1 = db.insert(burn())
        val id2 = db.insert(burn().copy(name = "Smolder"))
        assertNotEquals(id1, id2)
    }

    @Test
    fun `update preserves id across rename`() {
        val db = newDatabase()
        val id = db.insert(burn())
        val renamed = burn().copy(name = "Inferno")
        db.update(id, renamed)
        assertEquals(id, db.findIdByName("Inferno"))
        assertNull(db.findIdByName("Burn"))
    }

    @Test
    fun `update replaces all mutable fields atomically`() {
        val db = newDatabase()
        val id = db.insert(burn())
        val transformed = StatusEffect(
            name = "Frostbite",
            type = StatusType.Affliction.Wound,
            properties = listOf(Property.Ice, Property.Dark),
            stackable = false,
            description = "Numbs the target.",
        )
        db.update(id, transformed)

        val identified = db.identified.single()
        assertEquals(id, identified.id)
        assertEquals(transformed, identified.statusEffect)
    }

    @Test
    fun `deleteById removes the row`() {
        val db = newDatabase()
        val id = db.insert(burn())
        db.deleteById(id)
        assertEquals(emptyList<IdentifiedStatusEffect>(), db.identified)
    }

    @Test
    fun `findIdByName returns null when absent`() {
        val db = newDatabase()
        assertNull(db.findIdByName("Nope"))
    }

    @Test
    fun `replaceAll wipes existing rows and re-inserts`() {
        val db = newDatabase()
        val firstId = db.insert(burn())

        val replacement = burn().copy(name = "Replacement")
        db.replaceAll(listOf(replacement))

        assertNull(db.findIdByName("Burn"))
        val identified = db.identified
        assertEquals(1, identified.size)
        assertEquals(replacement, identified.single().statusEffect)
        assertNotEquals(firstId, identified.single().id)
    }
}
