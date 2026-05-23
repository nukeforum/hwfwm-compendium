package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun fire() = Essence.of("Fire", "burns", Rarity.Common, false) as Essence.Manifestation
    private fun water() = Essence.of("Water", "flows", Rarity.Common, false) as Essence.Manifestation
    private fun earth() = Essence.of("Earth", "stable", Rarity.Common, false) as Essence.Manifestation

    @Test
    fun `insertManifestation assigns positive id`() {
        val db = newDatabase()
        val id = db.insertManifestation(fire())
        assertTrue("id positive", id > 0)
        assertEquals(listOf(IdentifiedManifestation(id, fire())), db.identifiedManifestations)
    }

    @Test
    fun `updateManifestation preserves id across rename`() {
        val db = newDatabase()
        val id = db.insertManifestation(fire())
        val renamed = Essence.of("Inferno", "burns", Rarity.Common, false) as Essence.Manifestation
        db.updateManifestation(id, renamed)
        assertEquals(id, db.findManifestationIdByName("Inferno"))
        assertNull(db.findManifestationIdByName("Fire"))
    }

    @Test
    fun `deleteManifestationById removes the row`() {
        val db = newDatabase()
        val id = db.insertManifestation(fire())
        db.deleteManifestationById(id)
        assertEquals(emptyList<IdentifiedManifestation>(), db.identifiedManifestations)
    }

    @Test
    fun `insertConfluence with tagged essence refs round trips raw`() {
        val db = newDatabase()
        val fireId = db.insertManifestation(fire())
        val waterId = db.insertManifestation(water())
        val earthId = db.insertManifestation(earth())

        val rawSet = RawConfluenceSet(
            essence1Ref = "contr:$fireId",
            essence2Ref = "contr:$waterId",
            essence3Ref = "contr:$earthId",
            isRestricted = false,
        )
        val confluenceId = db.insertConfluence("Steam", isRestricted = false, sets = listOf(rawSet))

        val identified = db.identifiedConfluences
        assertEquals(1, identified.size)
        assertEquals(confluenceId, identified.single().id)
        assertEquals("Steam", identified.single().name)
        assertEquals(listOf(rawSet), identified.single().sets)
    }

    @Test
    fun `insertConfluence preserves canon-tagged ref strings verbatim`() {
        val db = newDatabase()
        val rawSet = RawConfluenceSet(
            essence1Ref = "canon:Fire",
            essence2Ref = "canon:Water",
            essence3Ref = "canon:Earth",
            isRestricted = false,
        )
        db.insertConfluence("Steam", isRestricted = false, sets = listOf(rawSet))

        assertEquals(listOf(rawSet), db.identifiedConfluences.single().sets)
    }

    @Test
    fun `updateConfluence replaces sets but keeps id stable`() {
        val db = newDatabase()
        val fireId = db.insertManifestation(fire())
        val waterId = db.insertManifestation(water())
        val earthId = db.insertManifestation(earth())
        val airName = "Air"  // canonical-only essence; encoded as canon:Air

        val original = RawConfluenceSet(
            essence1Ref = "contr:$fireId",
            essence2Ref = "contr:$waterId",
            essence3Ref = "contr:$earthId",
            isRestricted = false,
        )
        val id = db.insertConfluence("Steam", false, listOf(original))

        val replaced = RawConfluenceSet(
            essence1Ref = "contr:$fireId",
            essence2Ref = "contr:$waterId",
            essence3Ref = "canon:$airName",
            isRestricted = false,
        )
        db.updateConfluence(id, "Storm", isRestricted = true, sets = listOf(replaced))

        val identified = db.identifiedConfluences.single()
        assertEquals(id, identified.id)
        assertEquals("Storm", identified.name)
        assertEquals(true, identified.isRestricted)
        assertEquals(listOf(replaced), identified.sets)
    }

    @Test
    fun `deleteConfluenceById removes the confluence and its sets`() {
        val db = newDatabase()
        val rawSet = RawConfluenceSet("canon:A", "canon:B", "canon:C", false)
        val id = db.insertConfluence("Doomed", false, listOf(rawSet))

        db.deleteConfluenceById(id)

        assertEquals(emptyList<IdentifiedConfluence>(), db.identifiedConfluences)
    }

    @Test
    fun `findManifestationIdByName returns null when absent`() {
        val db = newDatabase()
        assertNull(db.findManifestationIdByName("Nope"))
    }

    @Test
    fun `findConfluenceIdByName returns null when absent`() {
        val db = newDatabase()
        assertNull(db.findConfluenceIdByName("Nope"))
    }

    @Test
    fun `replaceAll encodes contributed members as contr and unknown as canon`() {
        val db = newDatabase()
        val steam = Essence.of(
            name = "Steam",
            restricted = false,
            ConfluenceSet(setOf(fire(), water(), earth())),
        )

        db.replaceAll(listOf(fire(), water(), earth(), steam))

        val identifiedConf = db.identifiedConfluences.single()
        assertEquals("Steam", identifiedConf.name)
        // All three members were inserted as manifestations, so all three refs are contr:<id>.
        identifiedConf.sets.single().let { set ->
            assertTrue("essence1 should be contr-tagged: ${set.essence1Ref}", set.essence1Ref.startsWith("contr:"))
            assertTrue("essence2 should be contr-tagged: ${set.essence2Ref}", set.essence2Ref.startsWith("contr:"))
            assertTrue("essence3 should be contr-tagged: ${set.essence3Ref}", set.essence3Ref.startsWith("contr:"))
        }
    }

    @Test
    fun `replaceAll wipes existing rows on each call`() {
        val db = newDatabase()
        val firstId = db.insertManifestation(fire())

        db.replaceAll(listOf(water()))

        assertNull(db.findManifestationIdByName("Fire"))
        val identified = db.identifiedManifestations
        assertEquals(1, identified.size)
        assertEquals("Water", identified.single().manifestation.name)
        assertNotEquals(firstId, identified.single().id)
    }
}
