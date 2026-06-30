package wizardry.compendium.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search matches essence name regardless of query case`() = runTest {
        val repo = FakeEssenceRepo(listOf(manifestation("Fire"), manifestation("Wind")))
        val vm = SearchViewModel(repo, dispatcher)

        val collector = launch { vm.state.collect {} }

        for (query in listOf("fire", "Fire", "FIRE", "fIrE")) {
            vm.setFilterTerm(query)
            advanceUntilIdle()

            val names = vm.successEssenceNames()
            assertEquals("query \"$query\" should match Fire only", listOf("Fire"), names)
        }

        collector.cancel()
    }

    @Test
    fun `exact-case search still returns the matching essence`() = runTest {
        val repo = FakeEssenceRepo(listOf(manifestation("Fire"), manifestation("Wind")))
        val vm = SearchViewModel(repo, dispatcher)

        val collector = launch { vm.state.collect {} }

        vm.setFilterTerm("Wind")
        advanceUntilIdle()

        assertEquals(listOf("Wind"), vm.successEssenceNames())

        collector.cancel()
    }

    private fun SearchViewModel.successEssenceNames(): List<String> =
        (state.value as SearchUiState.Success).essences.map { it.name }
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private class FakeEssenceRepo(
    essences: List<Essence>,
) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(essences)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(
        originalName: String,
        manifestation: Essence.Manifestation,
    ) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(
        originalName: String,
        confluence: Essence.Confluence,
    ) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}
