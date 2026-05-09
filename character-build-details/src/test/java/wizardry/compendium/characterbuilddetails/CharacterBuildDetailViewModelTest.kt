package wizardry.compendium.characterbuilddetails

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.model.CharacterBuild

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load by name emits Success`() = runTest {
        val repo = FakeRepo(listOf(build("Jason"), build("Humphrey")))
        val vm = CharacterBuildDetailViewModel(repo).also { it.ioDispatcher = dispatcher }

        vm.load("Humphrey")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Success } as CharacterBuildDetailUiState.Success
        assertEquals("Humphrey", state.build.name)
    }

    @Test
    fun `load by unknown name emits Error`() = runTest {
        val vm = CharacterBuildDetailViewModel(FakeRepo(emptyList())).also { it.ioDispatcher = dispatcher }

        vm.load("ghost")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Error }
        assertTrue(state is CharacterBuildDetailUiState.Error)
    }

    @Test
    fun `flow update refreshes the loaded build`() = runTest {
        val repo = FakeRepo(listOf(build("Jason", race = "Outworlder")))
        val vm = CharacterBuildDetailViewModel(repo).also { it.ioDispatcher = dispatcher }

        vm.load("Jason")
        advanceUntilIdle()

        repo.update(listOf(build("Jason", race = "Earthling")))
        advanceUntilIdle()

        val state = vm.state.first { (it as? CharacterBuildDetailUiState.Success)?.build?.race == "Earthling" }
        assertEquals("Earthling", (state as CharacterBuildDetailUiState.Success).build.race)
    }

    private fun build(name: String, race: String = "Race"): CharacterBuild =
        CharacterBuild(name = name, race = race, racialAbilities = emptyList())

    private class FakeRepo(initial: List<CharacterBuild>) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds() = flow.value
        override suspend fun getBuild(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild) = ContributionResult.Success
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        fun update(next: List<CharacterBuild>) { flow.value = next }
    }
}
