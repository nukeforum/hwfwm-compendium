package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Resource
import kotlin.time.Duration.Companion.seconds

class AbilityListingDatabaseTest {

    private fun newDatabase(): AbilityListingDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AbilityListingDatabase(driver)
    }

    @Test
    fun `name only listing round trips`() {
        val db = newDatabase()
        val listing = Ability.Listing(name = "Bare", effects = emptyList())

        db.writeAll(listOf(listing))

        assertEquals(listOf(listing), db.readAll())
    }

    @Test
    fun `listing with full effect round trips`() {
        val db = newDatabase()
        val effect = Effect.AbilityEffect(
            rank = Rank.Bronze,
            type = AbilityType.Spell,
            properties = listOf(Property.Fire, Property.Magic, Property.Zone),
            cost = listOf(
                Cost.Upfront(Amount.Low, Resource.Mana),
                Cost.Ongoing(Amount.VeryLow, Resource.Stamina),
            ),
            cooldown = 30.seconds,
            description = "A scorching ring of flame.",
            replacementKey = "fireball-v2",
        )
        val listing = Ability.Listing(name = "Inferno Ring", effects = listOf(effect))

        db.writeAll(listOf(listing))

        assertEquals(listOf(listing), db.readAll())
    }

    @Test
    fun `multiple effects preserve order`() {
        val db = newDatabase()
        val ironEffect = baseEffect(Rank.Iron, "Iron tier description")
        val bronzeEffect = baseEffect(Rank.Bronze, "Bronze tier description")
        val silverEffect = baseEffect(Rank.Silver, "Silver tier description")
        val listing = Ability.Listing(
            name = "Tiered Ability",
            effects = listOf(ironEffect, bronzeEffect, silverEffect),
        )

        db.writeAll(listOf(listing))

        assertEquals(listOf(listing), db.readAll())
    }

    @Test
    fun `cost none round trips`() {
        val db = newDatabase()
        val effect = baseEffect(Rank.Iron, "passive").copy(cost = listOf(Cost.None))
        val listing = Ability.Listing(name = "Passive", effects = listOf(effect))

        db.writeAll(listOf(listing))

        assertEquals(listOf(listing), db.readAll())
    }

    @Test
    fun `null replacement key round trips`() {
        val db = newDatabase()
        val effect = baseEffect(Rank.Iron, "no replacement").copy(replacementKey = null)
        val listing = Ability.Listing(name = "Plain", effects = listOf(effect))

        db.writeAll(listOf(listing))

        assertEquals(listOf(listing), db.readAll())
    }

    @Test
    fun `multiple listings round trip sorted by name`() {
        val db = newDatabase()
        val zebra = Ability.Listing(name = "Zebra", effects = emptyList())
        val apple = Ability.Listing(
            name = "Apple",
            effects = listOf(baseEffect(Rank.Iron, "apple desc")),
        )

        db.writeAll(listOf(zebra, apple))

        assertEquals(listOf(apple, zebra), db.readAll())
    }

    @Test
    fun `writeAll replaces previous contents`() {
        val db = newDatabase()
        val first = Ability.Listing(
            name = "First",
            effects = listOf(baseEffect(Rank.Iron, "first desc")),
        )
        val second = Ability.Listing(
            name = "Second",
            effects = listOf(baseEffect(Rank.Bronze, "second desc")),
        )

        db.writeAll(listOf(first))
        db.writeAll(listOf(second))

        assertEquals(listOf(second), db.readAll())
    }

    private fun baseEffect(rank: Rank, description: String) = Effect.AbilityEffect(
        rank = rank,
        type = AbilityType.SpecialAttack,
        properties = listOf(Property.Melee),
        cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Stamina)),
        cooldown = 5.seconds,
        description = description,
        replacementKey = null,
    )
}
