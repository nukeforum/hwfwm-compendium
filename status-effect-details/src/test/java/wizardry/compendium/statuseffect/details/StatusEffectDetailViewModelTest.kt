package wizardry.compendium.statuseffect.details

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setup() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun teardown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun effect(name: String) = StatusEffect(
        name = name, type = StatusType.Affliction.Curse,
        properties = emptyList(), stackable = false, description = "",
    )

    private class FakeRepo(
        private val items: List<StatusEffect>,
        private val contributions: Set<String> = emptySet(),
    ) : StatusEffectRepository {
        override val statusEffects = flowOf(items)
        override val conflicts = flowOf(emptyList<StatusEffectConflict>())
        override suspend fun getStatusEffects() = items
        override suspend fun getContributions() = items.filter { it.name in contributions }
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
        override suspend fun isContribution(name: String) = name in contributions
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    }

    @Test
    fun `load found effect emits Success with isContribution flag`() = runTest {
        val burn = effect("Burn")
        val vm = StatusEffectDetailViewModel(FakeRepo(listOf(burn), contributions = setOf("Burn")))
        vm.load("Burn")
        val state = vm.state.value
        assertTrue(state is StatusEffectDetailUiState.Success)
        state as StatusEffectDetailUiState.Success
        assertEquals(burn, state.effect)
        assertTrue(state.isContribution)
    }

    @Test
    fun `load missing effect emits Error`() = runTest {
        val vm = StatusEffectDetailViewModel(FakeRepo(emptyList()))
        vm.load("Nope")
        assertTrue(vm.state.value is StatusEffectDetailUiState.Error)
    }
}
