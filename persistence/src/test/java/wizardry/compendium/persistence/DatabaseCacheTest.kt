package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType

class DatabaseCacheTest {

    private fun newDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        CompendiumDatabase.Schema.create(it)
    }

    @Test
    fun `essence cache memoizes contents across reads`() {
        val database = EssenceDatabase(newDriver()).also {
            it.writeAll(listOf(Essence.of("Fire", "burns", Rarity.Common, false)))
        }
        val cache = DatabaseEssenceCache(database)

        val first = cache.contents
        val second = cache.contents

        assertSame(first, second)
    }

    @Test
    fun `essence cache memoizes empty database`() {
        val cache = DatabaseEssenceCache(EssenceDatabase(newDriver()))

        val first = cache.contents
        val second = cache.contents

        assertEquals(emptyList<Essence>(), first)
        assertSame(first, second)
    }

    @Test
    fun `essence cache writes through to underlying database`() {
        val database = EssenceDatabase(newDriver())
        val cache = DatabaseEssenceCache(database)
        val newList = listOf(Essence.of("Fire", "burns", Rarity.Common, false))

        cache.contents = newList

        assertEquals(newList, database.readAll())
    }

    @Test
    fun `essence cache returns the just-set value on read`() {
        val cache = DatabaseEssenceCache(EssenceDatabase(newDriver()))
        val newList = listOf(Essence.of("Fire", "burns", Rarity.Common, false))

        cache.contents = newList
        val readBack = cache.contents

        assertSame(newList, readBack)
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
