package wizardry.compendium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectContributionsToggleFlow
import wizardry.compendium.essences.dataloader.StatusEffectDataLoader
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.persistence.StatusEffectContributionsToggle

class DefaultStatusEffectRepositoryTest {

    private fun effect(name: String) = StatusEffect(
        name = name,
        type = StatusType.Affliction.Curse,
        properties = emptyList(),
        stackable = false,
        description = "",
    )

    private class FakeCache(initial: List<StatusEffect> = emptyList()) : StatusEffectCache {
        override var contents: List<StatusEffect> = initial
    }

    private class FakeLoader(private val data: List<StatusEffect>) : StatusEffectDataLoader {
        override suspend fun loadStatusEffectData(): List<StatusEffect> = data
    }

    private fun toggleOn() = object : StatusEffectContributionsToggle {
        override val isStatusEffectContributionsEnabled = true
    }

    private fun toggleOff() = object : StatusEffectContributionsToggle {
        override val isStatusEffectContributionsEnabled = false
    }

    private fun toggleFlowOn() = object : StatusEffectContributionsToggleFlow {
        override val statusEffectContributionsEnabled = MutableStateFlow(true)
    }

    @Test
    fun `getStatusEffects returns canonical only when toggle is off`() = runTest {
        val canonical = listOf(effect("Burn"))
        val contribs = listOf(effect("Chill"))
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(canonical),
            canonicalCache = FakeCache(),
            contributionsCache = FakeCache(contribs),
            toggle = toggleOff(),
            toggleFlow = toggleFlowOn(),
        )
        assertEquals(canonical, repo.getStatusEffects())
    }

    @Test
    fun `getStatusEffects merges canonical and contributions when toggle is on`() = runTest {
        val canonical = listOf(effect("Burn"))
        val contribs = listOf(effect("Chill"))
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(canonical),
            canonicalCache = FakeCache(),
            contributionsCache = FakeCache(contribs),
            toggle = toggleOn(),
            toggleFlow = toggleFlowOn(),
        )
        val merged = repo.getStatusEffects().map { it.name }
        assertEquals(listOf("Burn", "Chill"), merged)
    }

    @Test
    fun `getStatusEffects returns canonical only when contribution conflicts exist`() = runTest {
        val burn = effect("Burn")
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(listOf(burn)),
            canonicalCache = FakeCache(),
            contributionsCache = FakeCache(listOf(burn.copy(description = "user"))),
            toggle = toggleOn(),
            toggleFlow = toggleFlowOn(),
        )
        assertEquals(listOf(burn), repo.getStatusEffects())
    }

    @Test
    fun `save then delete round-trip`() = runTest {
        val canonical = listOf(effect("Burn"))
        val cache = FakeCache()
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(canonical),
            canonicalCache = FakeCache(),
            contributionsCache = cache,
            toggle = toggleOn(),
            toggleFlow = toggleFlowOn(),
        )
        val chill = effect("Chill")
        assertEquals(ContributionResult.Success, repo.saveStatusEffectContribution(chill))
        assertTrue(repo.isContribution("chill"))
        assertEquals(ContributionResult.Success, repo.deleteContribution("Chill"))
        assertTrue(cache.contents.isEmpty())
    }

    @Test
    fun `save fails when name collides with canonical`() = runTest {
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(listOf(effect("Burn"))),
            canonicalCache = FakeCache(),
            contributionsCache = FakeCache(),
            toggle = toggleOn(),
            toggleFlow = toggleFlowOn(),
        )
        val result = repo.saveStatusEffectContribution(effect("burn"))
        assertTrue(result is ContributionResult.Failure)
    }
}
