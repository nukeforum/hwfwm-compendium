package wizardry.compendium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceContributionsToggleFlow
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.EssenceContributionsToggle

class DefaultEssenceRepositoryConflictTest {

    @Test
    fun `toggle off returns canonical regardless of conflicts`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Wind")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = false)

        assertEquals(canonical, repo.getEssences())
    }

    @Test
    fun `toggle on with no conflicts merges contributions`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Sin")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        val result = repo.getEssences()
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Wind" })
        assertTrue(result.any { it.name == "Sin" })
    }

    @Test
    fun `toggle on with name conflict returns canonical only`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Wind")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(canonical, repo.getEssences())
    }

    @Test
    fun `toggle on with combination conflict returns canonical only`() = runTest {
        val canonicalTempest = confluence("Tempest", setOf(set("Wind", "Rain", "Storm")))
        val contributedDoom = confluence("Doom", setOf(set("Wind", "Rain", "Storm")))
        val repo = repository(
            canonical = listOf(canonicalTempest),
            contributions = listOf(contributedDoom),
            toggle = true,
        )

        assertEquals(listOf(canonicalTempest), repo.getEssences())
    }

    @Test
    fun `getConflicts surfaces both name and combination conflicts`() = runTest {
        val canonical = listOf(
            manifestation("Wind"),
            confluence("Tempest", setOf(set("A", "B", "C"))),
        )
        val contributions = listOf(
            manifestation("Wind"),                                         // name collision
            confluence("Doom", setOf(set("A", "B", "C"))),                  // combination collision
        )
        val repo = repository(canonical = canonical, contributions = contributions, toggle = true)

        val conflicts = repo.getConflicts()
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.any { it is EssenceConflict.NameCollision })
        assertTrue(conflicts.any { it is EssenceConflict.CombinationCollision })
    }

    @Test
    fun `deleting the conflicting contribution clears the conflict and re-enables merge`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Wind")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        // Initially gated to canonical because of the conflict
        assertEquals(canonical, repo.getEssences())
        assertEquals(1, repo.getConflicts().size)

        repo.deleteContribution("Wind")

        assertEquals(0, repo.getConflicts().size)
        assertEquals(canonical, repo.getEssences())
        assertFalse(repo.isContribution("Wind"))
    }

    @Test
    fun `removing a single conflicting combination keeps the rest of the contribution`() = runTest {
        val canonicalTempest = confluence("Tempest", setOf(set("A", "B", "C")))
        val originalDoom = confluence(
            "Doom",
            setOf(set("A", "B", "C"), set("D", "E", "F")),
        )
        val repo = repository(
            canonical = listOf(canonicalTempest),
            contributions = listOf(originalDoom),
            toggle = true,
        )

        assertEquals(1, repo.getConflicts().size)

        // Remove the offending combination only
        val cleanedDoom = originalDoom.copy(
            confluenceSets = originalDoom.confluenceSets.filterNot { it == set("A", "B", "C") }.toSet(),
        )
        repo.updateConfluenceContribution(cleanedDoom)

        assertEquals(0, repo.getConflicts().size)
        // Doom (cleaned) should now appear in merged results
        val merged = repo.getEssences()
        val doomInMerged = merged.firstOrNull { it.name == "Doom" } as? Essence.Confluence
        assertEquals(setOf(set("D", "E", "F")), doomInMerged?.confluenceSets)
    }
}

private fun repository(
    canonical: List<Essence>,
    contributions: List<Essence>,
    toggle: Boolean,
): DefaultEssenceRepository {
    val canonicalCache = FakeEssenceCache(canonical)
    val contributionsCache = FakeEssenceCache(contributions)
    val toggleSource = FakeEssenceToggle(toggle)
    val toggleFlow = FakeEssenceToggleFlow(toggle)
    val loader = FakeEssenceDataLoader(canonical)
    return DefaultEssenceRepository(
        dataLoader = loader,
        canonicalCache = canonicalCache,
        contributionsCache = contributionsCache,
        toggle = toggleSource,
        toggleFlow = toggleFlow,
    )
}

private class FakeEssenceCache(initial: List<Essence>) : EssenceCache {
    override var contents: List<Essence> = initial
}

private class FakeEssenceToggle(override val isEssenceContributionsEnabled: Boolean) :
    EssenceContributionsToggle

private class FakeEssenceToggleFlow(initial: Boolean) : EssenceContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val essenceContributionsEnabled: Flow<Boolean> = state
}

private class FakeEssenceDataLoader(private val data: List<Essence>) : EssenceDataLoader {
    override suspend fun loadEssenceData(): List<Essence> = data
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun confluence(name: String, combinations: Set<ConfluenceSet>): Essence.Confluence =
    Essence.Confluence(name = name, confluenceSets = combinations, isRestricted = false)

private fun set(a: String, b: String, c: String): ConfluenceSet =
    ConfluenceSet(manifestation(a), manifestation(b), manifestation(c))
