package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.IdentifiedAwakeningStone
import wizardry.compendium.preferences.AwakeningStoneContributionsToggle
import wizardry.compendium.preferences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggle
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggleFlow

class DefaultAwakeningStoneRepositoryRenameTest {

    @Test
    fun `updateAwakeningStoneContribution preserves id across rename`() = runTest {
        val repo = repository(canonical = emptyList(), contributions = listOf(stone("OldName")), toggle = true)
        val result = repo.updateAwakeningStoneContribution(
            originalName = "OldName",
            stone = stone("NewName"),
        )
        assertEquals(ContributionResult.Success, result)
        val contributions = repo.getContributions()
        assertEquals(1, contributions.size)
        assertEquals("NewName", contributions.single().name)
    }

    @Test
    fun `rename collides with canonical returns error`() = runTest {
        val repo = repository(
            canonical = listOf(stone("Granite")),
            contributions = listOf(stone("Marble")),
            toggle = true,
        )
        val result = repo.updateAwakeningStoneContribution("Marble", stone("Granite"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `rename collides with another contribution returns error`() = runTest {
        val repo = repository(
            canonical = emptyList(),
            contributions = listOf(stone("Alpha"), stone("Beta")),
            toggle = true,
        )
        val result = repo.updateAwakeningStoneContribution("Alpha", stone("Beta"))
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `no-op rename (same name) succeeds`() = runTest {
        val repo = repository(
            canonical = emptyList(),
            contributions = listOf(stone("Granite")),
            toggle = true,
        )
        val result = repo.updateAwakeningStoneContribution("Granite", stone("Granite"))
        assertEquals(ContributionResult.Success, result)
        assertEquals(1, repo.getContributions().size)
    }

    @Test
    fun `checkDeleteImpact is structurally empty -- awakening stones have no referencing entities`() = runTest {
        // No table in the v6 schema references awakening_stone, so the repository's
        // checkDeleteImpact is hardcoded to DeleteImpact(). Locking in that
        // structural invariant: if a future schema change adds a referencing
        // table (e.g. a build slot that holds a stone), this test must be
        // rewritten to actually drive that lookup.
        val canonical = repository(canonical = listOf(stone("Granite")), contributions = emptyList(), toggle = false)
        assertTrue(canonical.checkDeleteImpact("Granite").isEmpty)

        val contributed = repository(canonical = emptyList(), contributions = listOf(stone("Hand-Carved")), toggle = true)
        assertTrue(contributed.checkDeleteImpact("Hand-Carved").isEmpty)

        val absent = repository(canonical = emptyList(), contributions = emptyList(), toggle = true)
        assertTrue(absent.checkDeleteImpact("DoesNotExist").isEmpty)
    }
}

private fun repository(
    canonical: List<AwakeningStone>,
    contributions: List<AwakeningStone>,
    toggle: Boolean,
): DefaultAwakeningStoneRepository {
    return DefaultAwakeningStoneRepository(
        dataLoader = RenameTestFakeDataLoader(canonical),
        canonicalCache = RenameTestFakeCache(canonical),
        contributionsCache = RenameTestFakeCache(contributions),
        toggle = RenameTestFakeToggle(toggle),
        toggleFlow = RenameTestFakeToggleFlow(toggle),
        essenceRepository = RenameTestFakeEssenceRepository(),
        essencesAsStonesToggle = RenameTestFakeEssencesAsStonesToggle(false),
        essencesAsStonesToggleFlow = RenameTestFakeEssencesAsStonesToggleFlow(false),
    )
}

private class RenameTestFakeCache(initial: List<AwakeningStone>) : AwakeningStoneCache {
    private val rows = initial.mapIndexed { i, s -> IdentifiedAwakeningStone(i.toLong(), s) }.toMutableList()
    private var nextId = initial.size.toLong()
    override val identified: List<IdentifiedAwakeningStone> get() = rows.toList()
    override fun insert(stone: AwakeningStone): Long {
        val id = nextId++; rows.add(IdentifiedAwakeningStone(id, stone)); return id
    }
    override fun update(id: Long, stone: AwakeningStone) {
        val idx = rows.indexOfFirst { it.id == id }; if (idx >= 0) rows[idx] = IdentifiedAwakeningStone(id, stone)
    }
    override fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override fun findIdByName(name: String): Long? = rows.firstOrNull { it.stone.name == name }?.id
    override fun replaceAll(stones: List<AwakeningStone>) {
        rows.clear(); nextId = 0; stones.forEach { rows.add(IdentifiedAwakeningStone(nextId++, it)) }
    }
}

private class RenameTestFakeToggle(override val isAwakeningStoneContributionsEnabled: Boolean) :
    AwakeningStoneContributionsToggle

private class RenameTestFakeToggleFlow(initial: Boolean) : AwakeningStoneContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val awakeningStoneContributionsEnabled: Flow<Boolean> = state
}

private class RenameTestFakeEssencesAsStonesToggle(
    override val isEssencesAsAwakeningStonesEnabled: Boolean,
) : EssencesAsAwakeningStonesToggle

private class RenameTestFakeEssencesAsStonesToggleFlow(initial: Boolean) :
    EssencesAsAwakeningStonesToggleFlow {
    private val state = MutableStateFlow(initial)
    override val essencesAsAwakeningStonesEnabled: Flow<Boolean> = state
}

private class RenameTestFakeDataLoader(private val data: List<AwakeningStone>) :
    AwakeningStoneDataLoader {
    override suspend fun loadAwakeningStoneData(): List<AwakeningStone> = data
}

private class RenameTestFakeEssenceRepository : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>): ContributionResult = ContributionResult.Success
    override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)
