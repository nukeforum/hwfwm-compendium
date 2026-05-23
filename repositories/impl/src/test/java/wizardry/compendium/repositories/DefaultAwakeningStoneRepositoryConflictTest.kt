package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
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

class DefaultAwakeningStoneRepositoryConflictTest {

    @Test
    fun `toggle off returns canonical even with conflicting contribution`() = runTest {
        val canonical = listOf(stone("Granite"))
        val contribution = stone("Granite")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = false)

        assertEquals(canonical, repo.getAwakeningStones())
    }

    @Test
    fun `toggle on with no conflicts merges contributions`() = runTest {
        val canonical = listOf(stone("Granite"))
        val contribution = stone("Marble")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        val merged = repo.getAwakeningStones()
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "Granite" })
        assertTrue(merged.any { it.name == "Marble" })
    }

    @Test
    fun `toggle on with name conflict returns canonical only`() = runTest {
        val canonical = listOf(stone("Granite"))
        val contribution = stone("Granite")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(canonical, repo.getAwakeningStones())
    }

    @Test
    fun `getConflicts returns the name collision`() = runTest {
        val canonical = stone("Granite")
        val contribution = stone("Granite")
        val repo = repository(canonical = listOf(canonical), contributions = listOf(contribution), toggle = true)

        val conflicts = repo.getConflicts()
        assertEquals(1, conflicts.size)
    }

    @Test
    fun `deleting the conflict clears the gate`() = runTest {
        val canonical = listOf(stone("Granite"))
        val contribution = stone("Granite")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(1, repo.getConflicts().size)
        repo.deleteContribution("Granite")
        assertEquals(0, repo.getConflicts().size)
    }

    @Test
    fun `essences-as-stones off leaves stones unchanged`() = runTest {
        val canonical = listOf(stone("Granite"))
        val essences = listOf(manifestation("Magma"))
        val repo = repository(
            canonical = canonical,
            contributions = emptyList(),
            toggle = false,
            essencesAsStonesEnabled = false,
            essences = essences,
        )

        assertEquals(canonical, repo.getAwakeningStones())
    }

    @Test
    fun `essences-as-stones on adds non-overlapping essences`() = runTest {
        val canonical = listOf(stone("Granite"))
        val essences = listOf(manifestation("Magma"), manifestation("Wing"))
        val repo = repository(
            canonical = canonical,
            contributions = emptyList(),
            toggle = false,
            essencesAsStonesEnabled = true,
            essences = essences,
        )

        val result = repo.getAwakeningStones()
        assertEquals(3, result.size)
        assertTrue(result.any { it.name == "Granite" })
        assertTrue(result.any { it.name == "Magma" })
        assertTrue(result.any { it.name == "Wing" })
    }

    @Test
    fun `essences-as-stones dedupes exact name match against canonical`() = runTest {
        val canonical = listOf(stone("Magma"))
        val essences = listOf(manifestation("Magma"))
        val repo = repository(
            canonical = canonical,
            contributions = emptyList(),
            toggle = false,
            essencesAsStonesEnabled = true,
            essences = essences,
        )

        val result = repo.getAwakeningStones()
        assertEquals(1, result.size)
        assertEquals("Magma", result.single().name)
    }

    @Test
    fun `essences-as-stones dedupes partial name match Dark vs Darkness`() = runTest {
        val canonical = listOf(stone("Darkness"))
        val essences = listOf(manifestation("Dark"))
        val repo = repository(
            canonical = canonical,
            contributions = emptyList(),
            toggle = false,
            essencesAsStonesEnabled = true,
            essences = essences,
        )

        val result = repo.getAwakeningStones()
        assertEquals(1, result.size)
        assertEquals("Darkness", result.single().name)
    }

    @Test
    fun `essences-as-stones dedupes against contribution stones too`() = runTest {
        val canonical = emptyList<AwakeningStone>()
        val contribution = stone("Magma Burst")
        val essences = listOf(manifestation("Magma"))
        val repo = repository(
            canonical = canonical,
            contributions = listOf(contribution),
            toggle = true,
            essencesAsStonesEnabled = true,
            essences = essences,
        )

        val result = repo.getAwakeningStones()
        assertEquals(1, result.size)
        assertEquals("Magma Burst", result.single().name)
    }
}

private fun repository(
    canonical: List<AwakeningStone>,
    contributions: List<AwakeningStone>,
    toggle: Boolean,
    essencesAsStonesEnabled: Boolean = false,
    essences: List<Essence> = emptyList(),
): DefaultAwakeningStoneRepository {
    return DefaultAwakeningStoneRepository(
        dataLoader = FakeAwakeningStoneDataLoader(canonical),
        canonicalCache = FakeAwakeningStoneCache(canonical),
        contributionsCache = FakeAwakeningStoneCache(contributions),
        toggle = FakeAwakeningStoneToggle(toggle),
        toggleFlow = FakeAwakeningStoneToggleFlow(toggle),
        essenceRepository = FakeEssenceRepository(essences),
        essencesAsStonesToggle = FakeEssencesAsStonesToggle(essencesAsStonesEnabled),
        essencesAsStonesToggleFlow = FakeEssencesAsStonesToggleFlow(essencesAsStonesEnabled),
    )
}

private class FakeAwakeningStoneCache(initial: List<AwakeningStone>) : AwakeningStoneCache {
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

private class FakeAwakeningStoneToggle(override val isAwakeningStoneContributionsEnabled: Boolean) :
    AwakeningStoneContributionsToggle

private class FakeAwakeningStoneToggleFlow(initial: Boolean) : AwakeningStoneContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val awakeningStoneContributionsEnabled: Flow<Boolean> = state
}

private class FakeEssencesAsStonesToggle(
    override val isEssencesAsAwakeningStonesEnabled: Boolean,
) : EssencesAsAwakeningStonesToggle

private class FakeEssencesAsStonesToggleFlow(initial: Boolean) :
    EssencesAsAwakeningStonesToggleFlow {
    private val state = MutableStateFlow(initial)
    override val essencesAsAwakeningStonesEnabled: Flow<Boolean> = state
}

private class FakeAwakeningStoneDataLoader(private val data: List<AwakeningStone>) :
    AwakeningStoneDataLoader {
    override suspend fun loadAwakeningStoneData(): List<AwakeningStone> = data
}

private class FakeEssenceRepository(private val data: List<Essence>) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(data)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = data
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        unsupported()
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = unsupported()
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = unsupported()
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = unsupported()
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        unsupported()
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult =
        unsupported()

    private fun unsupported(): ContributionResult =
        throw UnsupportedOperationException("not used in this test")
}

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "$name essence", rarity = Rarity.Common, restricted = false)
