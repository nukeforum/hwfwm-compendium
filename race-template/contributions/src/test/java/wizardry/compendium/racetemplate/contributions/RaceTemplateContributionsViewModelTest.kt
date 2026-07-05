package wizardry.compendium.racetemplate.contributions

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.RaceTemplateRepository
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class RaceTemplateContributionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // --- Mode -----------------------------------------------------------

    @Test
    fun `Create mode initial form is blank`() = runTest {
        val vm = create(savedName = null)
        advanceUntilIdle()

        val mode = vm.mode.first { it is RaceTemplateContributionsViewModel.Mode.Create }
        assertEquals(RaceTemplateContributionsViewModel.Mode.Create, mode)
        assertEquals("", vm.formState.value.name)
        assertTrue(vm.formState.value.racialAbilities.isEmpty())
    }

    @Test
    fun `Edit mode loads existing template into form`() = runTest {
        val existing = RaceTemplate("Golem", sixRacials())
        val vm = create(savedName = "Golem", existing = listOf(existing))
        advanceUntilIdle()

        val mode = vm.mode.first { it is RaceTemplateContributionsViewModel.Mode.Edit.Ready }
        assertTrue(mode is RaceTemplateContributionsViewModel.Mode.Edit.Ready)
        assertEquals("Golem", vm.formState.value.name)
        assertEquals(6, vm.formState.value.racialAbilities.size)
    }

    @Test
    fun `Edit mode emits NotFound when name doesn't match`() = runTest {
        val vm = create(savedName = "ghost", existing = emptyList())
        advanceUntilIdle()

        val mode = vm.mode.first { it is RaceTemplateContributionsViewModel.Mode.Edit.NotFound }
        assertTrue(mode is RaceTemplateContributionsViewModel.Mode.Edit.NotFound)
    }

    // --- Racial-only filter rule ---------------------------------------

    @Test
    fun `racialAbilityCandidates offers only racial abilities, never non-racial`() = runTest {
        val racials = (1..3).map { racialListing("r$it") }
        val spells = (1..3).map { spellListing("s$it") }
        val vm = create(savedName = null, listings = racials + spells)
        advanceUntilIdle()

        val candidates = vm.racialAbilityCandidates().map { it.name }.toSet()

        assertEquals(setOf("r1", "r2", "r3"), candidates)
        assertTrue(spells.map { it.name }.none { it in candidates })
    }

    // --- 6-cap on add ---------------------------------------------------

    @Test
    fun `addRacialAbility enforces 6-cap`() = runTest {
        val racials = (1..7).map { racialListing("r$it") }
        val vm = create(savedName = null, listings = racials)
        advanceUntilIdle()

        racials.take(6).forEach { vm.addRacialAbility(it) }
        vm.addRacialAbility(racials[6])
        advanceUntilIdle()

        assertEquals(6, vm.formState.value.racialAbilities.size)
    }

    @Test
    fun `addRacialAbility ignores duplicates by name`() = runTest {
        val r = racialListing("r1")
        val vm = create(savedName = null, listings = listOf(r))
        advanceUntilIdle()

        vm.addRacialAbility(r)
        vm.addRacialAbility(r)
        advanceUntilIdle()

        assertEquals(1, vm.formState.value.racialAbilities.size)
    }

    // --- exactly-6 save constraint -------------------------------------

    @Test
    fun `isComplete is true only at exactly 6 racial abilities`() = runTest {
        val racials = (1..6).map { racialListing("r$it") }
        val vm = create(savedName = null, listings = racials)
        advanceUntilIdle()

        assertFalse(vm.formState.value.isComplete)
        racials.take(5).forEach { vm.addRacialAbility(it) }
        assertFalse(vm.formState.value.isComplete)
        vm.addRacialAbility(racials[5])
        assertTrue(vm.formState.value.isComplete)
    }

    @Test
    fun `save is blocked below 6 racial abilities with a structured error`() = runTest {
        val racials = (1..6).map { racialListing("r$it") }
        val repo = FakeRaceTemplateRepo(emptyList())
        val vm = create(savedName = null, listings = racials, repo = repo)
        advanceUntilIdle()

        vm.setName("Golem")
        racials.take(5).forEach { vm.addRacialAbility(it) }
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is RaceTemplateContributionsViewModel.SaveState.Error }
            as RaceTemplateContributionsViewModel.SaveState.Error
        assertTrue(state.message.contains("exactly 6"))
        assertTrue("nothing should be persisted", repo.allNames().isEmpty())
    }

    @Test
    fun `save is blocked when name is blank`() = runTest {
        val racials = (1..6).map { racialListing("r$it") }
        val repo = FakeRaceTemplateRepo(emptyList())
        val vm = create(savedName = null, listings = racials, repo = repo)
        advanceUntilIdle()

        racials.forEach { vm.addRacialAbility(it) }
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is RaceTemplateContributionsViewModel.SaveState.Error }
            as RaceTemplateContributionsViewModel.SaveState.Error
        assertTrue(state.message.contains("Name"))
        assertTrue(repo.allNames().isEmpty())
    }

    @Test
    fun `save succeeds at exactly 6 racial abilities and persists the template`() = runTest {
        val racials = (1..6).map { racialListing("r$it") }
        val repo = FakeRaceTemplateRepo(emptyList())
        val vm = create(savedName = null, listings = racials, repo = repo)
        advanceUntilIdle()

        vm.setName("Golem")
        racials.forEach { vm.addRacialAbility(it) }
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is RaceTemplateContributionsViewModel.SaveState.Success }
        assertTrue(state is RaceTemplateContributionsViewModel.SaveState.Success)
        assertEquals(listOf("Golem"), repo.allNames())
        assertEquals(6, repo.byName("Golem")!!.racialAbilities.size)
    }

    @Test
    fun `save rejects duplicate name in create mode`() = runTest {
        val racials = (1..6).map { racialListing("r$it") }
        val repo = FakeRaceTemplateRepo(listOf(RaceTemplate("Golem", sixRacials())))
        val vm = create(savedName = null, listings = racials, repo = repo)
        advanceUntilIdle()

        vm.setName("Golem")
        racials.forEach { vm.addRacialAbility(it) }
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is RaceTemplateContributionsViewModel.SaveState.Error }
            as RaceTemplateContributionsViewModel.SaveState.Error
        assertTrue(state.message.contains("already exists"))
        assertEquals(1, repo.allNames().size)
    }

    @Test
    fun `deleteContribution in edit mode removes the template`() = runTest {
        val existing = RaceTemplate("Golem", sixRacials())
        val repo = FakeRaceTemplateRepo(listOf(existing))
        val vm = create(savedName = "Golem", existing = listOf(existing), repo = repo)
        advanceUntilIdle()
        vm.mode.first { it is RaceTemplateContributionsViewModel.Mode.Edit.Ready }

        vm.deleteContribution()
        advanceUntilIdle()

        val state = vm.saveState.first { it is RaceTemplateContributionsViewModel.SaveState.Deleted }
        assertTrue(state is RaceTemplateContributionsViewModel.SaveState.Deleted)
        assertTrue(repo.allNames().isEmpty())
    }

    // --- Helpers --------------------------------------------------------

    private fun racialListing(name: String): Ability.Listing = Ability.Listing(
        name = name,
        effects = listOf(
            Effect.AbilityEffect(
                rank = Rank.Iron, type = AbilityType.RacialAbility, properties = emptyList(),
                cost = emptyList(), cooldown = Duration.ZERO, description = "", replacementKey = null,
            ),
        ),
    )

    private fun spellListing(name: String): Ability.Listing = Ability.Listing(
        name = name,
        effects = listOf(
            Effect.AbilityEffect(
                rank = Rank.Iron, type = AbilityType.Spell, properties = emptyList(),
                cost = emptyList(), cooldown = Duration.ZERO, description = "", replacementKey = null,
            ),
        ),
    )

    private fun sixRacials(): List<Ability.Listing> = (1..6).map { racialListing("r$it") }

    private fun create(
        savedName: String?,
        existing: List<RaceTemplate> = emptyList(),
        listings: List<Ability.Listing> = emptyList(),
        repo: FakeRaceTemplateRepo = FakeRaceTemplateRepo(existing),
    ): RaceTemplateContributionsViewModel {
        val savedState = SavedStateHandle()
        if (savedName != null) savedState["name"] = savedName
        return RaceTemplateContributionsViewModel(
            savedStateHandle = savedState,
            repository = repo,
            abilityListingRepository = FakeAbilityListingRepo(listings),
        )
    }

    private class FakeRaceTemplateRepo(initial: List<RaceTemplate>) : RaceTemplateRepository {
        private val flow = MutableStateFlow(initial)
        override val raceTemplates: Flow<List<RaceTemplate>> = flow
        override suspend fun getRaceTemplates() = flow.value
        override suspend fun getRaceTemplate(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveRaceTemplateContribution(template: RaceTemplate): ContributionResult {
            flow.value = flow.value.filterNot { it.name == template.name } + template
            return ContributionResult.Success
        }
        override suspend fun deleteContribution(name: String): ContributionResult {
            if (flow.value.none { it.name == name }) return ContributionResult.Failure("nope")
            flow.value = flow.value.filterNot { it.name == name }
            return ContributionResult.Success
        }
        fun allNames(): List<String> = flow.value.map { it.name }
        fun byName(name: String): RaceTemplate? = flow.value.firstOrNull { it.name == name }
    }

    private class FakeAbilityListingRepo(private val data: List<Ability.Listing>) : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(data)
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings() = data
        override suspend fun getContributions() = emptyList<Ability.Listing>()
        override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
    }
}
