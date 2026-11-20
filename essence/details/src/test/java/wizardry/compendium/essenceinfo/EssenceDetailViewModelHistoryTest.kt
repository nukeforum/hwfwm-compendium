package wizardry.compendium.essenceinfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.essenceinfo.EssenceDetailUiState.Success.ConfluenceUiState
import wizardry.compendium.essenceinfo.EssenceDetailUiState.Success.ManifestationUiState
import wizardry.compendium.share.EssenceShareUseCase

/**
 * The detail screen remembers where the user came from: each essence shows a
 * "previous" essence, and back returns to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EssenceDetailViewModelHistoryTest {

    private val dispatcher = StandardTestDispatcher()

    private val flame = manifestation("Flame")
    private val ash = manifestation("Ash")
    private val smoke = manifestation("Smoke")
    private val inferno = Essence.Confluence(
        name = "Inferno",
        confluenceSets = setOf(ConfluenceSet(flame, ash, smoke)),
        isRestricted = false,
    )
    private val repo = FakeEssenceRepo(listOf(flame, ash, smoke, inferno))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `the first essence loaded has no previous essence`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.load("Flame")
        advanceUntilIdle()

        assertEquals(
            ManifestationUiState(flame, previousEssence = null, isContribution = false, knownConfluences = listOf(inferno)),
            vm.state.value,
        )
    }

    @Test
    fun `an essence opened from another shows that one as its previous essence`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load("Flame")
        advanceUntilIdle()

        vm.load(inferno)
        advanceUntilIdle()

        assertEquals(
            ConfluenceUiState(inferno, previousEssence = flame, isContribution = false),
            vm.state.value,
        )
    }

    @Test
    fun `going back shows the previous essence with nothing before it`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load("Flame")
        advanceUntilIdle()
        vm.load(inferno)
        advanceUntilIdle()

        vm.goBack()
        advanceUntilIdle()

        assertEquals(
            ManifestationUiState(flame, previousEssence = null, isContribution = false, knownConfluences = listOf(inferno)),
            vm.state.value,
        )
    }

    @Test
    fun `going back twice retraces the path one step at a time`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load("Flame")
        advanceUntilIdle()
        vm.load(inferno)
        advanceUntilIdle()
        vm.load(ash)
        advanceUntilIdle()

        vm.goBack()
        advanceUntilIdle()
        assertEquals(
            ConfluenceUiState(inferno, previousEssence = flame, isContribution = false),
            vm.state.value,
        )

        vm.goBack()
        advanceUntilIdle()
        assertEquals(
            ManifestationUiState(flame, previousEssence = null, isContribution = false, knownConfluences = listOf(inferno)),
            vm.state.value,
        )
    }

    @Test
    fun `a changed data set refreshes the essence on screen and keeps its previous essence`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load("Flame")
        advanceUntilIdle()
        vm.load(inferno)
        advanceUntilIdle()

        val restrictedInferno = inferno.copy(isRestricted = true)
        repo.replaceEssences(listOf(flame, ash, smoke, restrictedInferno))
        advanceUntilIdle()

        assertEquals(
            ConfluenceUiState(restrictedInferno, previousEssence = flame, isContribution = false),
            vm.state.value,
        )
    }

    private fun viewModel() = EssenceDetailViewModel(
        essenceRepository = repo,
        ioDispatcher = dispatcher,
        shareUseCase = EssenceShareUseCase(wireIo = stubWireIo(repo), essenceRepository = repo),
    )

    private fun manifestation(name: String): Essence.Manifestation =
        Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)
}
