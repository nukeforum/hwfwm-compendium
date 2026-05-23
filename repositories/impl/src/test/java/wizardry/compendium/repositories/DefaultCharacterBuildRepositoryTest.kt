package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.persistence.AbilityListingDatabase
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.persistence.RawConfluenceSet

class DefaultCharacterBuildRepositoryTest {

    // --- Helpers --------------------------------------------------------

    private fun newBuildDatabase(): CharacterBuildDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return CharacterBuildDatabase(driver)
    }

    private fun newAbilityListingDatabase(): AbilityListingDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return AbilityListingDatabase(driver)
    }

    private fun newEssenceDatabase(): EssenceDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return EssenceDatabase(driver)
    }

    private fun repository(
        buildDb: CharacterBuildDatabase = newBuildDatabase(),
        listingContribDb: AbilityListingDatabase = newAbilityListingDatabase(),
        essenceContribDb: EssenceDatabase = newEssenceDatabase(),
        canonicalEssences: List<Essence> = emptyList(),
        canonicalListings: List<Ability.Listing> = emptyList(),
    ): DefaultCharacterBuildRepository = DefaultCharacterBuildRepository(
        database = buildDb,
        abilityListingContributionsCache = listingContribDb,
        essenceContributionsCache = essenceContribDb,
        essenceRepository = FakeBuildEssenceRepository(canonicalEssences),
        abilityListingRepository = FakeBuildListingRepository(canonicalListings),
    )

    private fun manifestation(name: String): Essence.Manifestation =
        Essence.of(name = name, description = "$name essence", rarity = Rarity.Common, restricted = false)

    private fun listing(name: String): Ability.Listing = Ability.Listing.of(name)

    private fun absorbed(essence: Essence, listings: List<Ability.Listing>): AbsorbedEssence {
        val abilities = listings.map { l ->
            Ability.Acquired(
                name = l.name,
                effects = l.effects,
                rank = Rank.Iron,
                tier = 0,
                progress = 0f,
                boundEssence = essence,
                listing = l,
            )
        }
        return AbsorbedEssence(essence = essence, abilities = abilities)
    }

    private fun simpleBuild(
        name: String = "TestBuild",
        race: String = "Human",
        racialAbilities: List<Ability.Listing> = emptyList(),
        attributes: Set<Attribute> = setOf(
            Attribute.Power(), Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
        ),
    ) = CharacterBuild(
        name = name,
        race = race,
        racialAbilities = racialAbilities,
        attributes = attributes,
    )

    // --- Tests ----------------------------------------------------------

    @Test
    fun `canonical-only refs encode as canon-colon and decode back to canonical entities`() = runBlocking {
        val fireEssence = manifestation("Fire")
        val flameBolt = listing("Flame Bolt")
        val tough = listing("Tough")

        val buildDb = newBuildDatabase()
        val repo = repository(
            buildDb = buildDb,
            canonicalEssences = listOf(fireEssence),
            canonicalListings = listOf(flameBolt, tough),
        )

        val build = simpleBuild(
            name = "FireMage",
            racialAbilities = listOf(tough),
            attributes = setOf(
                Attribute.Power(essence = absorbed(fireEssence, listOf(flameBolt))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )

        repo.saveBuildContribution(build)

        // Verify raw encoding is all canon: prefixed
        val rawRacials = buildDb.readAllRacialAbilities()
        assertEquals(1, rawRacials.size)
        assertEquals("canon:Tough", rawRacials.single().listingRef)

        val rawAttrs = buildDb.readAllAttributes()
        assertEquals(1, rawAttrs.size)
        assertEquals("canon:Fire", rawAttrs.single().essenceRef)

        val rawAcquired = buildDb.readAllAcquiredAbilities()
        assertEquals(1, rawAcquired.size)
        assertEquals("canon:Flame Bolt", rawAcquired.single().listingRef)

        // Verify read-back resolves correctly
        val result = repo.getBuilds().single()
        assertEquals("FireMage", result.name)
        assertEquals("Fire", result.Power.essence?.essence?.name)
        assertEquals(listOf("Flame Bolt"), result.Power.essence?.abilities?.map { it.name })
        assertEquals(listOf("Tough"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `contributed refs encode as contr-colon-id and decode back by id`() = runBlocking {
        val buildDb = newBuildDatabase()
        val listingDb = newAbilityListingDatabase()
        val essenceDb = newEssenceDatabase()

        // Insert contributions; capture their assigned ids
        val contribListing = listing("Custom Ability")
        val contribListingId = listingDb.insert(contribListing)

        val contribEssence = manifestation("Custom Essence")
        val contribEssenceId = essenceDb.insertManifestation(contribEssence)

        val repo = repository(
            buildDb = buildDb,
            listingContribDb = listingDb,
            essenceContribDb = essenceDb,
        )

        val build = simpleBuild(
            name = "ContribBuild",
            racialAbilities = listOf(contribListing),
            attributes = setOf(
                Attribute.Power(essence = absorbed(contribEssence, listOf(contribListing))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        repo.saveBuildContribution(build)

        // Verify encoding uses contr: with the assigned id
        val rawRacials = buildDb.readAllRacialAbilities()
        assertEquals("contr:$contribListingId", rawRacials.single().listingRef)

        val rawAttrs = buildDb.readAllAttributes()
        assertEquals("contr:$contribEssenceId", rawAttrs.single().essenceRef)

        val rawAcquired = buildDb.readAllAcquiredAbilities()
        assertEquals("contr:$contribListingId", rawAcquired.single().listingRef)

        // Verify read-back resolves correctly using the contributions cache
        val result = repo.getBuilds().single()
        assertEquals("Custom Essence", result.Power.essence?.essence?.name)
        assertEquals(listOf("Custom Ability"), result.Power.essence?.abilities?.map { it.name })
        assertEquals(listOf("Custom Ability"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `renamed contribution resolves by id not by old name`() = runBlocking {
        val buildDb = newBuildDatabase()
        val listingDb = newAbilityListingDatabase()
        val essenceDb = newEssenceDatabase()

        val originalListing = listing("Old Name")
        val id = listingDb.insert(originalListing)

        val contribEssence = manifestation("Fire")
        essenceDb.insertManifestation(contribEssence)

        val repo = repository(
            buildDb = buildDb,
            listingContribDb = listingDb,
            essenceContribDb = essenceDb,
            canonicalEssences = listOf(contribEssence),
        )

        val build = simpleBuild(
            name = "Renamed",
            racialAbilities = listOf(originalListing),
        )
        repo.saveBuildContribution(build)

        // Rename the contribution in-place
        val renamed = listing("New Name")
        listingDb.update(id, renamed)

        // Re-read: the build references contr:<id> which now maps to "New Name"
        val result = repo.getBuilds().single()
        assertEquals(listOf("New Name"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `mixed canonical and contributed refs in the same build`() = runBlocking {
        val buildDb = newBuildDatabase()
        val listingDb = newAbilityListingDatabase()
        val essenceDb = newEssenceDatabase()

        val canonEssence = manifestation("Fire")
        val canonListing = listing("Flame Bolt")

        val contribListing = listing("Custom Ability")
        val contribListingId = listingDb.insert(contribListing)

        val repo = repository(
            buildDb = buildDb,
            listingContribDb = listingDb,
            essenceContribDb = essenceDb,
            canonicalEssences = listOf(canonEssence),
            canonicalListings = listOf(canonListing, contribListing),
        )

        // Racial has both: canon listing (not in listingDb) + contrib listing (in listingDb)
        val build = simpleBuild(
            name = "Mixed",
            racialAbilities = listOf(canonListing, contribListing),
            attributes = setOf(
                Attribute.Power(essence = absorbed(canonEssence, listOf(canonListing))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        repo.saveBuildContribution(build)

        // Verify encoding: canon listing → canon:, contrib listing → contr:
        val rawRacials = buildDb.readAllRacialAbilities().sortedBy { it.ordinal }
        assertEquals("canon:Flame Bolt", rawRacials[0].listingRef)
        assertEquals("contr:$contribListingId", rawRacials[1].listingRef)

        // Read-back should resolve both
        val result = repo.getBuilds().single()
        assertEquals(listOf("Flame Bolt", "Custom Ability"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `unresolved canonical ref drops the ability slot silently`() = runBlocking {
        val buildDb = newBuildDatabase()

        // Build references an essence "Ghost" not in the canonical repository
        val ghostEssence = manifestation("Ghost")
        val build = simpleBuild(
            name = "GhostBuild",
            attributes = setOf(
                Attribute.Power(essence = absorbed(ghostEssence, emptyList())),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )

        // Manually write with canon: prefix (bypassing the live resolver)
        buildDb.upsert(build, object : wizardry.compendium.persistence.BuildRefResolver {
            override fun encodeListing(listing: Ability.Listing) = "canon:${listing.name}"
            override fun encodeEssence(essence: Essence) = "canon:${essence.name}"
        })

        // Repo canonical view does NOT include "Ghost"
        val repo = repository(buildDb = buildDb, canonicalEssences = emptyList())

        val result = repo.getBuilds().single()
        assertNull("expected Power slot empty when canonical essence missing", result.Power.essence)
    }

    @Test
    fun `unresolved canonical listing ref drops just that ability`() = runBlocking {
        val buildDb = newBuildDatabase()
        val fireEssence = manifestation("Fire")
        val known = listing("Flame Bolt")
        val unknown = listing("Ghost Ability")

        buildDb.upsert(
            simpleBuild(
                name = "PartialBuild",
                attributes = setOf(
                    Attribute.Power(essence = absorbed(fireEssence, listOf(known, unknown))),
                    Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
                ),
            ),
            object : wizardry.compendium.persistence.BuildRefResolver {
                override fun encodeListing(listing: Ability.Listing) = "canon:${listing.name}"
                override fun encodeEssence(essence: Essence) = "canon:${essence.name}"
            },
        )

        // Canonical view has "Fire" essence but only "Flame Bolt" listing
        val repo = repository(
            buildDb = buildDb,
            canonicalEssences = listOf(fireEssence),
            canonicalListings = listOf(known),
        )

        val result = repo.getBuilds().single()
        assertEquals(
            listOf("Flame Bolt"),
            result.Power.essence?.abilities?.map { it.name },
        )
    }

    @Test
    fun `unresolved contributed id drops the slot`() = runBlocking {
        val buildDb = newBuildDatabase()

        // Write a build referencing contr:999 which will never exist in the cache
        buildDb.upsert(
            simpleBuild(name = "StaleContrib"),
            object : wizardry.compendium.persistence.BuildRefResolver {
                override fun encodeListing(listing: Ability.Listing) = "canon:${listing.name}"
                override fun encodeEssence(essence: Essence) = "contr:999"
            },
        )
        // Force an attribute row to exist
        val buildDbForced = newBuildDatabase()
        val ghostEssence = manifestation("Force")
        buildDbForced.upsert(
            simpleBuild(
                name = "StaleContrib",
                attributes = setOf(
                    Attribute.Power(essence = absorbed(ghostEssence, emptyList())),
                    Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
                ),
            ),
            object : wizardry.compendium.persistence.BuildRefResolver {
                override fun encodeListing(listing: Ability.Listing) = "canon:${listing.name}"
                override fun encodeEssence(essence: Essence) = "contr:999"
            },
        )

        // Contributions cache is empty — id 999 not present
        val repo = repository(buildDb = buildDbForced)

        val result = repo.getBuilds().single()
        assertNull("expected Power slot empty when contributed essence id missing", result.Power.essence)
    }

    @Test
    fun `getBuild returns null for unknown name`() = runBlocking {
        val repo = repository()
        assertNull(repo.getBuild("ghost"))
    }

    @Test
    fun `saveBuildContribution upserts build — replace on same name`() = runBlocking {
        val buildDb = newBuildDatabase()
        val repo = repository(buildDb = buildDb)

        val original = simpleBuild(name = "Reaper", race = "Human")
        val updated = simpleBuild(name = "Reaper", race = "Smeldraxian")

        repo.saveBuildContribution(original)
        repo.saveBuildContribution(updated)

        val builds = buildDb.readAllBuilds()
        assertEquals(1, builds.size)
        assertEquals("Smeldraxian", builds.single().race)
    }

    @Test
    fun `deleteContribution removes the build`() = runBlocking {
        val buildDb = newBuildDatabase()
        val repo = repository(buildDb = buildDb)

        repo.saveBuildContribution(simpleBuild(name = "ToDelete"))
        assertEquals(1, buildDb.readAllBuilds().size)

        val result = repo.deleteContribution("ToDelete")
        assertEquals(ContributionResult.Success, result)
        assertTrue(buildDb.readAllBuilds().isEmpty())
    }

    @Test
    fun `deleteContribution on unknown name returns Failure`() = runBlocking {
        val repo = repository()

        val result = repo.deleteContribution("ghost")

        assertTrue(
            "expected Failure but got $result",
            result is ContributionResult.Failure,
        )
    }

    @Test
    fun `confluence essence hydrates intact when all set members resolve`() = runBlocking {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val dark = manifestation("Dark")
        val confluence = Essence.Confluence(
            name = "Sinister",
            confluenceSets = setOf(
                ConfluenceSet(setOf(sin, doom, dark), isRestricted = false),
            ),
            isRestricted = false,
        )
        val build = simpleBuild(
            name = "Jason",
            attributes = setOf(
                Attribute.Power(), Attribute.Speed(), Attribute.Spirit(),
                Attribute.Recovery(essence = absorbed(confluence, emptyList())),
            ),
        )

        val repo = repository(
            canonicalEssences = listOf(sin, doom, dark, confluence),
            canonicalListings = emptyList(),
        )
        // Save via the live resolver (all canonical)
        repo.saveBuildContribution(build)

        val result = repo.getBuilds().single()
        assertEquals("Sinister", result.Recovery.essence?.essence?.name)
        assertTrue(
            "expected Confluence, got ${result.Recovery.essence?.essence?.javaClass?.simpleName}",
            result.Recovery.essence?.essence is Essence.Confluence,
        )
    }
}

// --- Fakes --------------------------------------------------------------

private class FakeBuildEssenceRepository(private val data: List<Essence>) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(data)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = data
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        error("not used")
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = error("not used")
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = error("not used")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        error("not used")
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult =
        error("not used")
}

private class FakeBuildListingRepository(private val data: List<Ability.Listing>) :
    AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(data)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = data
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        error("not used")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        error("not used")
}
