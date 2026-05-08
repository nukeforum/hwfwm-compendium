package wizardry.compendium.characterbuild.search

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
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.model.CharacterBuild

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state shows all builds with empty filter`() = runTest {
        val repo = FakeRepo(listOf(build("Alpha"), build("Beta")))
        val vm = CharacterBuildSearchViewModel(repo)
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildSearchUiState.Success } as CharacterBuildSearchUiState.Success
        assertEquals(listOf("Alpha", "Beta"), state.builds.map { it.name })
        assertEquals("", state.filterTerm)
    }

    @Test
    fun `name filter is case-insensitive substring match`() = runTest {
        val repo = FakeRepo(listOf(build("Alpha"), build("Beta"), build("Gamma Beta")))
        val vm = CharacterBuildSearchViewModel(repo)
        advanceUntilIdle()

        vm.setFilterTerm("beta")
        advanceUntilIdle()

        val state = vm.state.first { (it as? CharacterBuildSearchUiState.Success)?.filterTerm == "beta" } as CharacterBuildSearchUiState.Success
        assertEquals(listOf("Beta", "Gamma Beta"), state.builds.map { it.name })
    }

    @Test
    fun `empty repo emits Success with empty list, not Loading`() = runTest {
        val vm = CharacterBuildSearchViewModel(FakeRepo(emptyList()))
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildSearchUiState.Success } as CharacterBuildSearchUiState.Success
        assertEquals(emptyList<CharacterBuild>(), state.builds)
    }

    private fun build(name: String): CharacterBuild =
        CharacterBuild(name = name, race = "Race", racialAbilities = emptyList())

    private class FakeRepo(initial: List<CharacterBuild>) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds(): List<CharacterBuild> = flow.value
        override suspend fun getBuild(name: String): CharacterBuild? = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult {
            flow.value = (flow.value.filterNot { it.name == build.name } + build)
            return ContributionResult.Success
        }
        override suspend fun deleteContribution(name: String): ContributionResult {
            flow.value = flow.value.filterNot { it.name == name }
            return ContributionResult.Success
        }
    }
}
