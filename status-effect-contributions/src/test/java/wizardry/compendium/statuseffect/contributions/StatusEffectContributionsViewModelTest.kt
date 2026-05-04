package wizardry.compendium.statuseffect.contributions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectContributionsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun teardown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun effect(name: String) = StatusEffect(
        name = name, type = StatusType.Affliction.Curse,
        properties = emptyList(), stackable = false, description = "",
    )

    private class FakeRepo(
        private val items: List<StatusEffect> = emptyList(),
        private val contributions: Set<String> = emptySet(),
        var saveResult: ContributionResult = ContributionResult.Success,
    ) : StatusEffectRepository {
        var lastSaved: StatusEffect? = null
        var lastUpdated: StatusEffect? = null
        var lastDeleted: String? = null
        override val statusEffects = flowOf(items)
        override val conflicts = flowOf(emptyList<StatusEffectConflict>())
        override suspend fun getStatusEffects() = items
        override suspend fun getContributions() = items.filter { it.name in contributions }
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult {
            lastSaved = effect; return saveResult
        }
        override suspend fun isContribution(name: String) = name in contributions
        override suspend fun deleteContribution(name: String): ContributionResult {
            lastDeleted = name; return saveResult
        }
        override suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult {
            lastUpdated = effect; return saveResult
        }
    }

    @Test
    fun `create mode saves a new effect`() = runTest {
        val repo = FakeRepo()
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(), repo)
        vm.save("Burn", StatusType.Affliction.Elemental, listOf(Property.Fire), stackable = true, description = "burn")
        advanceUntilIdle()
        assertEquals(StatusEffect(
            name = "Burn", type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire), stackable = true, description = "burn",
        ), repo.lastSaved)
        assertEquals(StatusEffectContributionsViewModel.SaveState.Success, vm.saveState.value)
    }

    @Test
    fun `edit mode loads existing then updates`() = runTest {
        val burn = effect("Burn")
        val repo = FakeRepo(items = listOf(burn), contributions = setOf("Burn"))
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(mapOf("name" to "Burn")), repo)
        advanceUntilIdle()
        assertTrue(vm.mode.value is StatusEffectContributionsViewModel.Mode.Edit.Ready)
        vm.save("Burn", StatusType.Affliction.Elemental, emptyList(), stackable = false, description = "")
        advanceUntilIdle()
        assertEquals("Burn", repo.lastUpdated?.name)
    }

    @Test
    fun `blank name yields error state`() = runTest {
        val repo = FakeRepo()
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(), repo)
        vm.save("", StatusType.Affliction.Curse, emptyList(), stackable = false, description = "")
        advanceUntilIdle()
        val s = vm.saveState.value
        assertTrue(s is StatusEffectContributionsViewModel.SaveState.Error)
    }
}
