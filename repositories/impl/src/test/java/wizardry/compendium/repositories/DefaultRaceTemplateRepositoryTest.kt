package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.persistence.AbilityListingDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.RaceTemplateDatabase

class DefaultRaceTemplateRepositoryTest {

    private fun newTemplateDatabase(): RaceTemplateDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return RaceTemplateDatabase(driver)
    }

    private fun newAbilityListingDatabase(): AbilityListingDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AbilityListingDatabase(driver)
    }

    private fun repository(
        templateDb: RaceTemplateDatabase = newTemplateDatabase(),
        listingContribDb: AbilityListingDatabase = newAbilityListingDatabase(),
        canonicalListings: List<Ability.Listing> = emptyList(),
    ): DefaultRaceTemplateRepository = DefaultRaceTemplateRepository(
        database = templateDb,
        abilityListingContributionsCache = listingContribDb,
        abilityListingRepository = FakeListingRepository(canonicalListings),
    )

    private fun listing(name: String): Ability.Listing = Ability.Listing.of(name)

    private fun template(name: String, racial: List<Ability.Listing>): RaceTemplate =
        RaceTemplate(name = name, racialAbilities = racial)

    @Test
    fun `canonical-only refs encode as canon-colon and decode back to canonical listings`() = runBlocking {
        val tough = listing("Tough")
        val regen = listing("Regeneration")
        val templateDb = newTemplateDatabase()
        val repo = repository(templateDb = templateDb, canonicalListings = listOf(tough, regen))

        repo.saveRaceTemplateContribution(template("Golem", listOf(tough, regen)))

        val raw = templateDb.readAllRacialAbilities()
        assertEquals(listOf("canon:Tough", "canon:Regeneration"), raw.map { it.listingRef })

        val result = repo.getRaceTemplate("Golem")!!
        assertEquals(listOf("Tough", "Regeneration"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `contributed refs encode as contr-colon-id and decode back by id`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val listingDb = newAbilityListingDatabase()

        val contrib = listing("Custom Trait")
        val id = listingDb.insert(contrib)

        val repo = repository(templateDb = templateDb, listingContribDb = listingDb)
        repo.saveRaceTemplateContribution(template("Custom Race", listOf(contrib)))

        assertEquals("contr:$id", templateDb.readAllRacialAbilities().single().listingRef)

        val result = repo.getRaceTemplate("Custom Race")!!
        assertEquals(listOf("Custom Trait"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `renamed contribution resolves by id not by old name`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val listingDb = newAbilityListingDatabase()

        val original = listing("Old Trait")
        val id = listingDb.insert(original)

        val repo = repository(templateDb = templateDb, listingContribDb = listingDb)
        repo.saveRaceTemplateContribution(template("Race", listOf(original)))

        listingDb.update(id, listing("New Trait"))

        val result = repo.getRaceTemplate("Race")!!
        assertEquals(listOf("New Trait"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `mixed canonical and contributed refs in the same template`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val listingDb = newAbilityListingDatabase()

        val canon = listing("Tough")
        val contrib = listing("Custom Trait")
        val contribId = listingDb.insert(contrib)

        val repo = repository(
            templateDb = templateDb,
            listingContribDb = listingDb,
            canonicalListings = listOf(canon, contrib),
        )
        repo.saveRaceTemplateContribution(template("Mixed", listOf(canon, contrib)))

        val raw = templateDb.readAllRacialAbilities().sortedBy { it.ordinal }
        assertEquals("canon:Tough", raw[0].listingRef)
        assertEquals("contr:$contribId", raw[1].listingRef)

        val result = repo.getRaceTemplate("Mixed")!!
        assertEquals(listOf("Tough", "Custom Trait"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `unresolved canonical listing ref drops just that ability`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val known = listing("Known")
        val unknown = listing("Ghost")

        // Write with canon: encoding for both, but the canonical view only has "Known".
        templateDb.upsert(
            template("Partial", listOf(known, unknown)),
            object : wizardry.compendium.persistence.RaceTemplateRefResolver {
                override fun encodeListing(listing: Ability.Listing) = "canon:${listing.name}"
            },
        )

        val repo = repository(templateDb = templateDb, canonicalListings = listOf(known))

        val result = repo.getRaceTemplate("Partial")!!
        assertEquals(listOf("Known"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `saveRaceTemplateContribution upserts — replace on same name`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val a = listing("A")
        val b = listing("B")
        val repo = repository(templateDb = templateDb, canonicalListings = listOf(a, b))

        repo.saveRaceTemplateContribution(template("Race", listOf(a)))
        repo.saveRaceTemplateContribution(template("Race", listOf(b)))

        assertEquals(1, templateDb.readAllRaceTemplates().size)
        assertEquals(listOf("canon:B"), templateDb.readAllRacialAbilities().map { it.listingRef })
    }

    @Test
    fun `deleteContribution removes the template`() = runBlocking {
        val templateDb = newTemplateDatabase()
        val repo = repository(templateDb = templateDb)

        repo.saveRaceTemplateContribution(template("ToDelete", emptyList()))
        assertEquals(1, templateDb.readAllRaceTemplates().size)

        assertEquals(ContributionResult.Success, repo.deleteContribution("ToDelete"))
        assertTrue(templateDb.readAllRaceTemplates().isEmpty())
    }

    @Test
    fun `deleteContribution on unknown name returns Failure`() = runBlocking {
        val repo = repository()
        assertTrue(repo.deleteContribution("ghost") is ContributionResult.Failure)
    }

    @Test
    fun `getRaceTemplate returns null for unknown name`() = runBlocking {
        assertNull(repository().getRaceTemplate("ghost"))
    }
}

// --- Fake -----------------------------------------------------------------

private class FakeListingRepository(private val data: List<Ability.Listing>) : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(data)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = data
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = error("not used")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult = error("not used")
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}
