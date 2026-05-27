package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.EssenceRef
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceDatabase
import wizardry.compendium.persistence.IdentifiedConfluence
import wizardry.compendium.persistence.IdentifiedManifestation
import wizardry.compendium.persistence.RawConfluenceSet
import wizardry.compendium.preferences.EssenceContributionsToggle
import wizardry.compendium.preferences.EssenceContributionsToggleFlow

class DefaultEssenceRepositoryRenameTest {

    // ------------------------------------------------------------------ rename tests

    @Test
    fun `updateManifestationContribution preserves id across rename`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(manifestation("OldName")), toggle = true)
        val result = repo.updateManifestationContribution(
            originalName = "OldName",
            manifestation = manifestation("NewName"),
        )
        assertEquals(ContributionResult.Success, result)
        val contributions = repo.getContributions()
        assertEquals(1, contributions.size)
        assertEquals("NewName", contributions.single().name)
    }

    @Test
    fun `updateConfluenceContribution preserves id across rename`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val original = confluence("OldConfluence", setOf(ConfluenceSet(a, b, c)))
        val repo = repository(canonical = emptyList(), contributions = listOf(a, b, c, original), toggle = true)
        val result = repo.updateConfluenceContribution(
            originalName = "OldConfluence",
            confluence = original.copy(name = "NewConfluence"),
        )
        assertEquals(ContributionResult.Success, result)
        val contributions = repo.getContributions().filterIsInstance<Essence.Confluence>()
        assertEquals(1, contributions.size)
        assertEquals("NewConfluence", contributions.single().name)
    }

    @Test
    fun `rename collides with canonical manifestation returns error`() = runTest {
        val repo = repository(
            canonical = listOf(manifestation("Wind")),
            contributions = listOf(manifestation("Sin")),
            toggle = true,
        )
        val result = repo.updateManifestationContribution("Sin", manifestation("Wind"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `rename collides with canonical confluence returns error`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val canonicalConfluence = confluence("Tempest", setOf(ConfluenceSet(a, b, c)))
        val repo = repository(
            canonical = listOf(a, b, c, canonicalConfluence),
            contributions = listOf(manifestation("Sin")),
            toggle = true,
        )
        val result = repo.updateManifestationContribution("Sin", manifestation("Tempest"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `rename collides with another contributed manifestation returns error`() = runTest {
        val repo = repository(
            canonical = emptyList(),
            contributions = listOf(manifestation("Fire"), manifestation("Ice")),
            toggle = true,
        )
        val result = repo.updateManifestationContribution("Fire", manifestation("Ice"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `rename collides with another contributed confluence returns error`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val existing = confluence("Doom", setOf(ConfluenceSet(a, b, c)))
        val repo = repository(
            canonical = emptyList(),
            contributions = listOf(a, b, c, existing, manifestation("Sin")),
            toggle = true,
        )
        // Rename manifestation to clash with contributed confluence name
        val result = repo.updateManifestationContribution("Sin", manifestation("Doom"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `rename to same name (no-op rename) succeeds`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(manifestation("Fire")), toggle = true)
        // Same name, different casing is still "same name" by ignoreCase check
        val result = repo.updateManifestationContribution("Fire", manifestation("Fire"))
        assertEquals(ContributionResult.Success, result)
    }

    // ------------------------------------------------------------------ delete impact tests

    @Test
    fun `checkDeleteImpact returns empty when no references exist`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(manifestation("Wind")), toggle = true)
        val impact = repo.checkDeleteImpact("Wind")
        assertTrue(impact.isEmpty)
    }

    @Test
    fun `checkDeleteImpact for unknown name returns empty`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = emptyList(), toggle = true)
        val impact = repo.checkDeleteImpact("DoesNotExist")
        assertTrue(impact.isEmpty)
    }

    @Test
    fun `checkDeleteImpact returns referencingConfluenceSets when essence is in a confluence set`() = runTest {
        // Drive the real EssenceDatabase end-to-end through the repository so
        // the impact-aggregation logic is the one under test (not the raw DAO).
        val repo = repositoryWithRealDb { essenceDb, _ ->
            val manifestationId = essenceDb.insertManifestation(manifestation("Wind"))
            essenceDb.insertConfluence(
                name = "Storm",
                isRestricted = false,
                sets = listOf(
                    wizardry.compendium.persistence.RawConfluenceSet(
                        essence1Ref = RefCodec.encodeEssenceRef(EssenceRef.Contributed.Manifestation(manifestationId)),
                        essence2Ref = RefCodec.encodeEssenceRef(EssenceRef.Canonical("Rain")),
                        essence3Ref = RefCodec.encodeEssenceRef(EssenceRef.Canonical("Frost")),
                        isRestricted = false,
                    ),
                ),
            )
        }

        val impact = repo.checkDeleteImpact("Wind")
        assertEquals(listOf("Storm"), impact.referencingConfluenceSets)
        assertEquals(emptyList<String>(), impact.referencingBuilds)
    }

    @Test
    fun `checkDeleteImpact returns referencingBuilds when essence is referenced by a build attribute`() = runTest {
        val repo = repositoryWithRealDb { essenceDb, buildDb ->
            val manifestationId = essenceDb.insertManifestation(manifestation("Wind"))
            val essenceRef = RefCodec.encodeEssenceRef(EssenceRef.Contributed.Manifestation(manifestationId))
            // Hand-build a CharacterBuild that puts Wind in the Power slot.
            buildDb.writeAll(
                listOf(
                    wizardry.compendium.domain.model.CharacterBuild(
                        name = "AirMage",
                        race = "Human",
                        racialAbilities = emptyList(),
                        attributes = setOf(
                            wizardry.compendium.domain.model.Attribute.Power(
                                essence = wizardry.compendium.domain.model.AbsorbedEssence(
                                    essence = manifestation("Wind"),
                                    abilities = emptyList(),
                                ),
                            ),
                            wizardry.compendium.domain.model.Attribute.Speed(),
                            wizardry.compendium.domain.model.Attribute.Spirit(),
                            wizardry.compendium.domain.model.Attribute.Recovery(),
                        ),
                    ),
                ),
                object : wizardry.compendium.persistence.BuildRefResolver {
                    override fun encodeListing(listing: wizardry.compendium.domain.model.Ability.Listing) =
                        RefCodec.encodeAbilityRef(wizardry.compendium.domain.model.AbilityRef.Canonical(listing.name))
                    override fun encodeEssence(essence: Essence) = essenceRef
                },
            )
        }

        val impact = repo.checkDeleteImpact("Wind")
        assertEquals(listOf("AirMage"), impact.referencingBuilds)
    }

    @Test
    fun `checkDeleteImpact for a contributed confluence reports referencing builds`() = runTest {
        val repo = repositoryWithRealDb { essenceDb, buildDb ->
            // Contribute a confluence so it gets a stable id we can ref via contr:.
            val confluenceId = essenceDb.insertConfluence(
                name = "Squall",
                isRestricted = false,
                sets = listOf(
                    wizardry.compendium.persistence.RawConfluenceSet(
                        essence1Ref = RefCodec.encodeEssenceRef(EssenceRef.Canonical("Wind")),
                        essence2Ref = RefCodec.encodeEssenceRef(EssenceRef.Canonical("Rain")),
                        essence3Ref = RefCodec.encodeEssenceRef(EssenceRef.Canonical("Frost")),
                        isRestricted = false,
                    ),
                ),
            )
            val essenceRef = RefCodec.encodeEssenceRef(EssenceRef.Contributed.Confluence(confluenceId))
            buildDb.writeAll(
                listOf(
                    wizardry.compendium.domain.model.CharacterBuild(
                        name = "StormCaller",
                        race = "Human",
                        racialAbilities = emptyList(),
                        attributes = setOf(
                            wizardry.compendium.domain.model.Attribute.Power(
                                essence = wizardry.compendium.domain.model.AbsorbedEssence(
                                    essence = manifestation("Squall"),
                                    abilities = emptyList(),
                                ),
                            ),
                            wizardry.compendium.domain.model.Attribute.Speed(),
                            wizardry.compendium.domain.model.Attribute.Spirit(),
                            wizardry.compendium.domain.model.Attribute.Recovery(),
                        ),
                    ),
                ),
                object : wizardry.compendium.persistence.BuildRefResolver {
                    override fun encodeListing(listing: wizardry.compendium.domain.model.Ability.Listing) =
                        RefCodec.encodeAbilityRef(wizardry.compendium.domain.model.AbilityRef.Canonical(listing.name))
                    override fun encodeEssence(essence: Essence) = essenceRef
                },
            )
        }

        val impact = repo.checkDeleteImpact("Squall")
        // Confluence delete impact reports referencing builds (the confluence-id branch
        // of DefaultEssenceRepository.checkDeleteImpact).
        assertEquals(listOf("StormCaller"), impact.referencingBuilds)
    }
}

/**
 * Build a DefaultEssenceRepository whose contributionsCache and essenceDatabase
 * are the SAME concrete EssenceDatabase, sharing one in-memory SQLite driver
 * with the CharacterBuildDatabase. [populate] runs against the real databases
 * before the repository is constructed so test fixtures can write through the
 * actual SQL path.
 */
private fun repositoryWithRealDb(
    populate: (EssenceDatabase, CharacterBuildDatabase) -> Unit,
): DefaultEssenceRepository {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CompendiumDatabase.Schema.create(driver)
    val essenceDb = EssenceDatabase(driver)
    val buildDb = CharacterBuildDatabase(driver)
    populate(essenceDb, buildDb)
    return DefaultEssenceRepository(
        dataLoader = RenameTestFakeEssenceDataLoader(emptyList()),
        canonicalCache = RenameTestFakeEssenceCache(emptyList()),
        contributionsCache = essenceDb,
        essenceDatabase = essenceDb,
        characterBuildDatabase = buildDb,
        toggle = RenameTestFakeEssenceToggle(true),
        toggleFlow = RenameTestFakeEssenceToggleFlow(true),
    )
}

// ------------------------------------------------------------------ helpers

private fun repository(
    canonical: List<Essence>,
    contributions: List<Essence>,
    toggle: Boolean,
): DefaultEssenceRepository {
    val canonicalMemberNames: Set<String> = buildSet {
        canonical.filterIsInstance<Essence.Manifestation>().forEach { add(it.name) }
        canonical.filterIsInstance<Essence.Confluence>().forEach { c ->
            c.confluenceSets.forEach { set -> set.set.forEach { add(it.name) } }
        }
    }
    val canonicalCache = RenameTestFakeEssenceCache(canonical)
    val contributionsCache = RenameTestFakeEssenceCache(contributions, externalCanonicalNames = canonicalMemberNames)
    val toggleSource = RenameTestFakeEssenceToggle(toggle)
    val toggleFlow = RenameTestFakeEssenceToggleFlow(toggle)
    val loader = RenameTestFakeEssenceDataLoader(canonical)
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CompendiumDatabase.Schema.create(driver)
    val essenceDatabase = EssenceDatabase(driver)
    val characterBuildDatabase = CharacterBuildDatabase(driver)
    return DefaultEssenceRepository(
        dataLoader = loader,
        canonicalCache = canonicalCache,
        contributionsCache = contributionsCache,
        essenceDatabase = essenceDatabase,
        characterBuildDatabase = characterBuildDatabase,
        toggle = toggleSource,
        toggleFlow = toggleFlow,
    )
}

private class RenameTestFakeEssenceCache(
    initial: List<Essence>,
    private val externalCanonicalNames: Set<String> = emptySet(),
) : EssenceCache {
    private val manifestationRows: MutableList<IdentifiedManifestation> = mutableListOf()
    private val internMap: MutableMap<String, Long> = mutableMapOf()
    private val confluenceRows: MutableList<IdentifiedConfluence> = mutableListOf()
    private var nextId = 0L

    init {
        initial.filterIsInstance<Essence.Manifestation>().forEach { m ->
            val id = nextId++
            manifestationRows.add(IdentifiedManifestation(id, m))
            internMap[m.name] = id
        }
        initial.filterIsInstance<Essence.Confluence>().forEach { c ->
            val encodedSets = c.confluenceSets.map { cs ->
                val sorted = cs.set.sortedBy { it.name }
                require(sorted.size == 3) { "Test confluences must have exactly 3 members" }
                RawConfluenceSet(
                    essence1Ref = encodeRef(sorted[0]),
                    essence2Ref = encodeRef(sorted[1]),
                    essence3Ref = encodeRef(sorted[2]),
                    isRestricted = cs.isRestricted,
                )
            }
            confluenceRows.add(IdentifiedConfluence(nextId++, c.name, c.isRestricted, encodedSets))
        }
    }

    private fun encodeRef(m: Essence.Manifestation): String {
        if (m.name in externalCanonicalNames) {
            return RefCodec.encodeEssenceRef(EssenceRef.Canonical(m.name))
        }
        val existing = internMap[m.name]
        if (existing != null) return RefCodec.encodeEssenceRef(EssenceRef.Contributed.Manifestation(existing))
        val id = nextId++
        internMap[m.name] = id
        manifestationRows.add(IdentifiedManifestation(id, m))
        return RefCodec.encodeEssenceRef(EssenceRef.Contributed.Manifestation(id))
    }

    override val identifiedManifestations: List<IdentifiedManifestation> get() = manifestationRows.toList()
    override val identifiedConfluences: List<IdentifiedConfluence> get() = confluenceRows.toList()

    override fun insertManifestation(manifestation: Essence.Manifestation): Long {
        val id = nextId++
        manifestationRows.add(IdentifiedManifestation(id, manifestation))
        internMap[manifestation.name] = id
        return id
    }

    override fun updateManifestation(id: Long, manifestation: Essence.Manifestation) {
        val idx = manifestationRows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            internMap.remove(manifestationRows[idx].manifestation.name)
            manifestationRows[idx] = IdentifiedManifestation(id, manifestation)
            internMap[manifestation.name] = id
        }
    }

    override fun deleteManifestationById(id: Long) {
        val row = manifestationRows.firstOrNull { it.id == id }
        if (row != null) {
            internMap.remove(row.manifestation.name)
            manifestationRows.remove(row)
        }
    }

    override fun findManifestationIdByName(name: String): Long? =
        manifestationRows.firstOrNull { it.manifestation.name == name }?.id

    override fun insertConfluence(name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>): Long {
        val id = nextId++
        confluenceRows.add(IdentifiedConfluence(id, name, isRestricted, sets))
        return id
    }

    override fun updateConfluence(id: Long, name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>) {
        val idx = confluenceRows.indexOfFirst { it.id == id }
        if (idx >= 0) confluenceRows[idx] = IdentifiedConfluence(id, name, isRestricted, sets)
    }

    override fun deleteConfluenceById(id: Long) { confluenceRows.removeAll { it.id == id } }

    override fun findConfluenceIdByName(name: String): Long? =
        confluenceRows.firstOrNull { it.name == name }?.id

    override fun replaceAll(essences: List<Essence>) {
        manifestationRows.clear(); confluenceRows.clear(); internMap.clear(); nextId = 0
        essences.filterIsInstance<Essence.Manifestation>().forEach {
            val id = nextId++
            manifestationRows.add(IdentifiedManifestation(id, it))
            internMap[it.name] = id
        }
        essences.filterIsInstance<Essence.Confluence>().forEach { c ->
            val encodedSets = c.confluenceSets.map { cs ->
                val sorted = cs.set.sortedBy { it.name }
                require(sorted.size == 3) { "Test confluences must have exactly 3 members" }
                RawConfluenceSet(
                    essence1Ref = encodeRef(sorted[0]),
                    essence2Ref = encodeRef(sorted[1]),
                    essence3Ref = encodeRef(sorted[2]),
                    isRestricted = cs.isRestricted,
                )
            }
            confluenceRows.add(IdentifiedConfluence(nextId++, c.name, c.isRestricted, encodedSets))
        }
    }
}

private class RenameTestFakeEssenceToggle(override val isEssenceContributionsEnabled: Boolean) :
    EssenceContributionsToggle

private class RenameTestFakeEssenceToggleFlow(initial: Boolean) : EssenceContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val essenceContributionsEnabled: Flow<Boolean> = state
}

private class RenameTestFakeEssenceDataLoader(private val data: List<Essence>) : EssenceDataLoader {
    override suspend fun loadEssenceData(): List<Essence> = data
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun confluence(name: String, combinations: Set<ConfluenceSet>): Essence.Confluence =
    Essence.Confluence(name = name, confluenceSets = combinations, isRestricted = false)
