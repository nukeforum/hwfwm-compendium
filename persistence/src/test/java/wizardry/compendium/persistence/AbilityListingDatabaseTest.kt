package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Resource
import kotlin.time.Duration.Companion.seconds

class AbilityListingDatabaseTest {

    private fun newDatabase(): AbilityListingDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AbilityListingDatabase(driver)
    }

    @Test
    fun `insert assigns a positive id and identified reads it back`() {
        val db = newDatabase()
        val listing = Ability.Listing(name = "Inferno Ring", effects = listOf(richEffect()))

        val id = db.insert(listing)

        assertTrue("id must be positive", id > 0)
        assertEquals(listOf(IdentifiedListing(id, listing)), db.identified)
    }

    @Test
    fun `consecutive inserts allocate distinct ids`() {
        val db = newDatabase()
        val id1 = db.insert(Ability.Listing(name = "A", effects = emptyList()))
        val id2 = db.insert(Ability.Listing(name = "B", effects = emptyList()))
        assertNotEquals(id1, id2)
    }

    @Test
    fun `update preserves id across name change`() {
        val db = newDatabase()
        val original = Ability.Listing(name = "Original", effects = listOf(simpleEffect("first")))
        val id = db.insert(original)

        val renamed = Ability.Listing(name = "Renamed", effects = listOf(simpleEffect("first")))
        db.update(id, renamed)

        assertEquals(listOf(IdentifiedListing(id, renamed)), db.identified)
        assertEquals(id, db.findIdByName("Renamed"))
        assertNull(db.findIdByName("Original"))
    }

    @Test
    fun `update replaces effects but keeps listing id stable`() {
        val db = newDatabase()
        val id = db.insert(
            Ability.Listing(name = "Tiered", effects = listOf(simpleEffect("iron"), simpleEffect("bronze")))
        )

        val replaced = Ability.Listing(name = "Tiered", effects = listOf(simpleEffect("only")))
        db.update(id, replaced)

        assertEquals(listOf(IdentifiedListing(id, replaced)), db.identified)
    }

    @Test
    fun `deleteById removes the listing and its effects`() {
        val db = newDatabase()
        val id = db.insert(Ability.Listing(name = "Doomed", effects = listOf(richEffect())))

        db.deleteById(id)

        assertEquals(emptyList<IdentifiedListing>(), db.identified)
    }

    @Test
    fun `findIdByName returns null when not found`() {
        val db = newDatabase()
        assertNull(db.findIdByName("Ghost"))
    }

    @Test
    fun `replaceAll wipes existing rows and re-inserts`() {
        val db = newDatabase()
        val firstId = db.insert(Ability.Listing(name = "First", effects = emptyList()))
        val replacement = Ability.Listing(name = "Replacement", effects = listOf(richEffect()))

        db.replaceAll(listOf(replacement))

        // First listing is gone (its id is no longer valid).
        assertNull(db.findIdByName("First"))
        val identified = db.identified
        assertEquals(1, identified.size)
        assertEquals(replacement, identified.single().listing)
        assertNotEquals(firstId, identified.single().id)
    }

    private fun richEffect() = Effect.AbilityEffect(
        rank = Rank.Bronze,
        type = AbilityType.Spell,
        properties = listOf(Property.Fire, Property.Magic),
        cost = listOf(Cost.Upfront(Amount.Low, Resource.Mana)),
        cooldown = 30.seconds,
        description = "Hot.",
        replacementKey = "fb",
    )

    private fun simpleEffect(description: String) = Effect.AbilityEffect(
        rank = Rank.Iron,
        type = AbilityType.SpecialAttack,
        properties = listOf(Property.Melee),
        cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Stamina)),
        cooldown = 5.seconds,
        description = description,
        replacementKey = null,
    )
}
