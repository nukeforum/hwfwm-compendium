package wizardry.compendium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.essences.dataloader.AwakeningStoneDataLoader
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.persistence.AwakeningStoneCache
import wizardry.compendium.persistence.AwakeningStoneContributionsToggle

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
}

private fun repository(
    canonical: List<AwakeningStone>,
    contributions: List<AwakeningStone>,
    toggle: Boolean,
): DefaultAwakeningStoneRepository {
    return DefaultAwakeningStoneRepository(
        dataLoader = FakeAwakeningStoneDataLoader(canonical),
        canonicalCache = FakeAwakeningStoneCache(canonical),
        contributionsCache = FakeAwakeningStoneCache(contributions),
        toggle = FakeAwakeningStoneToggle(toggle),
        toggleFlow = FakeAwakeningStoneToggleFlow(toggle),
    )
}

private class FakeAwakeningStoneCache(initial: List<AwakeningStone>) : AwakeningStoneCache {
    override var contents: List<AwakeningStone> = initial
}

private class FakeAwakeningStoneToggle(override val isAwakeningStoneContributionsEnabled: Boolean) :
    AwakeningStoneContributionsToggle

private class FakeAwakeningStoneToggleFlow(initial: Boolean) : AwakeningStoneContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val awakeningStoneContributionsEnabled: Flow<Boolean> = state
}

private class FakeAwakeningStoneDataLoader(private val data: List<AwakeningStone>) :
    AwakeningStoneDataLoader {
    override suspend fun loadAwakeningStoneData(): List<AwakeningStone> = data
}

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)
