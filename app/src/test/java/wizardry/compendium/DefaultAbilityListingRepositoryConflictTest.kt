package wizardry.compendium

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.AbilityListingContributionsToggleFlow
import wizardry.compendium.essences.dataloader.AbilityListingDataLoader
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.AbilityListingContributionsToggle

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
}

private fun repository(
    canonical: List<Ability.Listing>,
    contributions: List<Ability.Listing>,
    toggle: Boolean,
): DefaultAbilityListingRepository {
    return DefaultAbilityListingRepository(
        dataLoader = FakeAbilityListingDataLoader(canonical),
        canonicalCache = FakeAbilityListingCache(canonical),
        contributionsCache = FakeAbilityListingCache(contributions),
        toggle = FakeAbilityListingToggle(toggle),
        toggleFlow = FakeAbilityListingToggleFlow(toggle),
    )
}

private class FakeAbilityListingCache(initial: List<Ability.Listing>) : AbilityListingCache {
    override var contents: List<Ability.Listing> = initial
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
