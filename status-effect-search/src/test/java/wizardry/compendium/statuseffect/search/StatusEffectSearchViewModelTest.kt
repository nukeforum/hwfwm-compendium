package wizardry.compendium.statuseffect.search

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectSearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @After
    fun teardown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun effect(name: String, type: StatusType) = StatusEffect(
        name = name, type = type, properties = emptyList(),
        stackable = false, description = "",
    )

    private class FakeRepo(private val items: List<StatusEffect>) : StatusEffectRepository {
        override val statusEffects = flowOf(items)
        override val conflicts = flowOf(emptyList<StatusEffectConflict>())
        override suspend fun getStatusEffects() = items
        override suspend fun getContributions() = emptyList<StatusEffect>()
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    }

    @Test
    fun `unfiltered search returns all results`() = runTest {
        val items = listOf(
            effect("Burn", StatusType.Affliction.Elemental),
            effect("Bless", StatusType.Boon.Holy),
        )
        val vm = StatusEffectSearchViewModel(FakeRepo(items))
        val state = vm.state.value as StatusEffectSearchUiState.Success
        assertEquals(items.toSet(), state.effects.toSet())
    }

    @Test
    fun `name term filters case-insensitively`() = runTest {
        val burn = effect("Burn", StatusType.Affliction.Elemental)
        val bless = effect("Bless", StatusType.Boon.Holy)
        val vm = StatusEffectSearchViewModel(FakeRepo(listOf(burn, bless)))
        vm.setFilterTerm("burn")
        val state = vm.state.value as StatusEffectSearchUiState.Success
        assertEquals(listOf(burn), state.effects)
    }

    @Test
    fun `applying Boon top-level filter excludes afflictions`() = runTest {
        val burn = effect("Burn", StatusType.Affliction.Elemental)
        val bless = effect("Bless", StatusType.Boon.Holy)
        val vm = StatusEffectSearchViewModel(FakeRepo(listOf(burn, bless)))
        vm.applyFilter(StatusEffectSearchFilter.Boon(subtypes = emptySet()))
        val state = vm.state.value as StatusEffectSearchUiState.Success
        assertEquals(listOf(bless), state.effects)
    }

    @Test
    fun `subtype filter narrows within Affliction`() = runTest {
        val burn = effect("Burn", StatusType.Affliction.Elemental)
        val curse = effect("Hex", StatusType.Affliction.Curse)
        val bless = effect("Bless", StatusType.Boon.Holy)
        val vm = StatusEffectSearchViewModel(FakeRepo(listOf(burn, curse, bless)))
        vm.applyFilter(StatusEffectSearchFilter.Affliction(subtypes = setOf(StatusType.Affliction.Curse)))
        val state = vm.state.value as StatusEffectSearchUiState.Success
        assertEquals(listOf(curse), state.effects)
    }
}
