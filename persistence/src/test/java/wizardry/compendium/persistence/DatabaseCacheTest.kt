package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType

class DatabaseCacheTest {

    private fun newDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        CompendiumDatabase.Schema.create(it)
    }

    @Test
    fun `awakening stone cache reads from database`() {
        val driver = newDriver()
        val stone = AwakeningStone.of("Pebble", Rarity.Common)
        AwakeningStoneDatabase(driver).writeAll(listOf(stone))
        val cache = DatabaseAwakeningStoneCache(AwakeningStoneDatabase(driver))

        assertEquals(listOf(stone), cache.contents)
    }

    @Test
    fun `status effect cache reads from database`() {
        val driver = newDriver()
        val effect = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire),
            stackable = true,
            description = "fiery",
        )
        StatusEffectDatabase(driver).writeAll(listOf(effect))
        val cache = DatabaseStatusEffectCache(StatusEffectDatabase(driver))

        assertEquals(listOf(effect), cache.contents)
    }
}
