package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.essences.dataloader.AbilityListingDataLoader
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.AbilityListingDatabase
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.IdentifiedListing
import wizardry.compendium.preferences.AbilityListingContributionsToggle
import wizardry.compendium.preferences.AbilityListingContributionsToggleFlow

class DefaultAbilityListingRepositoryConflictTest {

    @Test
    fun `toggle off returns canonical even with conflicting contribution`() = runTest {
        val canonical = listOf(listing("Fireball"))
        val contribution = listing("Fireball")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = false)

        assertEquals(canonical, repo.getAbilityListings())
    }

    @Test
    fun `toggle on with no conflicts merges contributions`() = runTest {
        val canonical = listOf(listing("Fireball"))
        val contribution = listing("Frost")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        val merged = repo.getAbilityListings()
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "Fireball" })
        assertTrue(merged.any { it.name == "Frost" })
    }

    @Test
    fun `toggle on with name conflict returns canonical only`() = runTest {
        val canonical = listOf(listing("Fireball"))
        val contribution = listing("Fireball")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(canonical, repo.getAbilityListings())
    }

    @Test
    fun `deleting the conflict clears the gate`() = runTest {
        val canonical = listOf(listing("Fireball"))
        val contribution = listing("Fireball")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(1, repo.getConflicts().size)
        repo.deleteContribution("Fireball")
        assertEquals(0, repo.getConflicts().size)
    }

    @Test
    fun `update preserves the same id when name changes`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(listing("OldName")), toggle = true)
        val result = repo.updateAbilityListingContribution("OldName", listing("NewName"))
        assertEquals(ContributionResult.Success, result)
        val contributions = repo.getContributions()
        assertEquals(1, contributions.size)
        assertEquals("NewName", contributions.single().name)
    }

    @Test
    fun `update fails when new name collides with a canonical listing`() = runTest {
        val repo = repository(canonical = listOf(listing("Fireball")), contributions = listOf(listing("Frost")), toggle = true)
        val result = repo.updateAbilityListingContribution("Frost", listing("Fireball"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `update fails when new name collides with another contribution`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(listing("Alpha"), listing("Beta")), toggle = true)
        val result = repo.updateAbilityListingContribution("Alpha", listing("Beta"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `update succeeds when new name is the same as originalName case-insensitively`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(listing("Fireball")), toggle = true)
        val result = repo.updateAbilityListingContribution("Fireball", listing("fireball"))
        assertEquals(ContributionResult.Success, result)
    }

    @Test
    fun `checkDeleteImpact returns empty when no builds reference this listing`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(listing("Frost")), toggle = true)
        val impact = repo.checkDeleteImpact("Frost")
        assertTrue(impact.isEmpty)
    }

    @Test
    fun `checkDeleteImpact returns referencingBuilds when a build references this listing`() = runTest {
        // Drive the real CharacterBuildDatabase + AbilityListingDatabase through
        // the repository so the buildsReferencingListingRef query is the one
        // under test.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        val buildDb = CharacterBuildDatabase(driver)
        val contributionsCache = AbilityListingDatabase(driver)
        val listing = listing("Frost")
        val listingId = contributionsCache.insert(listing)

        // Write a build whose racial slot references the contributed listing.
        buildDb.writeAll(
            listOf(
                wizardry.compendium.domain.model.CharacterBuild(
                    name = "FrostMage",
                    race = "Human",
                    racialAbilities = listOf(listing),
                    attributes = setOf(
                        wizardry.compendium.domain.model.Attribute.Power(),
                        wizardry.compendium.domain.model.Attribute.Speed(),
                        wizardry.compendium.domain.model.Attribute.Spirit(),
                        wizardry.compendium.domain.model.Attribute.Recovery(),
                    ),
                ),
            ),
            object : wizardry.compendium.persistence.BuildRefResolver {
                override fun encodeListing(l: Ability.Listing) =
                    wizardry.compendium.domain.model.RefCodec.encodeAbilityRef(
                        wizardry.compendium.domain.model.AbilityRef.Contributed(listingId),
                    )
                override fun encodeEssence(essence: wizardry.compendium.domain.model.Essence) =
                    wizardry.compendium.domain.model.RefCodec.encodeEssenceRef(
                        wizardry.compendium.domain.model.EssenceRef.Canonical(essence.name),
                    )
            },
        )

        val repo = DefaultAbilityListingRepository(
            dataLoader = FakeAbilityListingDataLoader(emptyList()),
            canonicalCache = FakeAbilityListingCache(emptyList()),
            contributionsCache = contributionsCache,
            characterBuildDatabase = buildDb,
            toggle = FakeAbilityListingToggle(true),
            toggleFlow = FakeAbilityListingToggleFlow(true),
        )
        val impact = repo.checkDeleteImpact("Frost")
        assertEquals(listOf("FrostMage"), impact.referencingBuilds)
    }

    @Test
    fun `update fails when originalName not found`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = emptyList(), toggle = true)
        val result = repo.updateAbilityListingContribution("DoesNotExist", listing("NewName"))
        assertTrue(result is ContributionResult.Failure)
    }
}

private fun repository(
    canonical: List<Ability.Listing>,
    contributions: List<Ability.Listing>,
    toggle: Boolean,
): DefaultAbilityListingRepository {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CompendiumDatabase.Schema.create(driver)
    val buildDatabase = CharacterBuildDatabase(driver)
    return DefaultAbilityListingRepository(
        dataLoader = FakeAbilityListingDataLoader(canonical),
        canonicalCache = FakeAbilityListingCache(canonical),
        contributionsCache = FakeAbilityListingCache(contributions),
        characterBuildDatabase = buildDatabase,
        toggle = FakeAbilityListingToggle(toggle),
        toggleFlow = FakeAbilityListingToggleFlow(toggle),
    )
}

private class FakeAbilityListingCache(initial: List<Ability.Listing>) : AbilityListingCache {
    private val rows = initial.mapIndexed { i, l -> IdentifiedListing(i.toLong(), l) }.toMutableList()
    private var nextId = initial.size.toLong()
    override val identified: List<IdentifiedListing> get() = rows.toList()
    override fun insert(listing: Ability.Listing): Long {
        val id = nextId++; rows.add(IdentifiedListing(id, listing)); return id
    }
    override fun update(id: Long, listing: Ability.Listing) {
        val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0) rows[idx] = IdentifiedListing(id, listing)
    }
    override fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override fun findIdByName(name: String): Long? = rows.firstOrNull { it.listing.name == name }?.id
    override fun replaceAll(listings: List<Ability.Listing>) {
        rows.clear(); nextId = 0; listings.forEach { rows.add(IdentifiedListing(nextId++, it)) }
    }
    override fun bulkRewriteStatusTokens(
        rewrite: (effectId: Long, description: String) -> String?,
    ): Int = 0
}

private class FakeAbilityListingToggle(override val isAbilityListingContributionsEnabled: Boolean) :
    AbilityListingContributionsToggle

private class FakeAbilityListingToggleFlow(initial: Boolean) : AbilityListingContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val abilityListingContributionsEnabled: Flow<Boolean> = state
}

private class FakeAbilityListingDataLoader(private val data: List<Ability.Listing>) :
    AbilityListingDataLoader {
    override suspend fun loadAbilityListingData(): List<Ability.Listing> = data
}

private fun listing(name: String): Ability.Listing = Ability.Listing(name = name, effects = emptyList())
