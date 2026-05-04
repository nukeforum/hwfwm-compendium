package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectDatabaseTest {

    private fun newDb(): StatusEffectDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return StatusEffectDatabase(driver)
    }

    @Test
    fun `round-trip preserves all fields`() {
        val db = newDb()
        val sample = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.DamageOverTime, Property.Fire),
            stackable = true,
            description = "Deals fire damage over time.",
        )

        db.writeAll(listOf(sample))
        val readBack = db.readAll()

        assertEquals(listOf(sample), readBack)
    }

    @Test
    fun `empty properties list round-trips`() {
        val db = newDb()
        val sample = StatusEffect(
            name = "Recovery",
            type = StatusType.Boon.UnTyped,
            properties = emptyList(),
            stackable = false,
            description = "",
        )
        db.writeAll(listOf(sample))
        assertEquals(listOf(sample), db.readAll())
    }
}
