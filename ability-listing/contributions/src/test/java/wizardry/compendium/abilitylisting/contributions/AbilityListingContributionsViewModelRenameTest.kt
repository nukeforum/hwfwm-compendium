package wizardry.compendium.abilitylisting.contributions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.share.AbilityListingShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AbilityListingContributionsViewModelRenameTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `editing calls updateAbilityListingContribution with original editName`() = runTest {
        val capturedCalls = mutableListOf<Pair<String, String>>()
        val repo = object : SpyListingRepo() {
            override suspend fun updateAbilityListingContribution(
                originalName: String,
                listing: Ability.Listing,
            ): ContributionResult {
                capturedCalls += originalName to listing.name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "OldName", repo = repo)
        vm.saveAbilityListing("NewName")

        assertEquals(1, capturedCalls.size)
        assertEquals("OldName", capturedCalls.single().first)
        assertEquals("NewName", capturedCalls.single().second)
    }

    @Test
    fun `editing with name collision emits SaveState Error`() = runTest {
        val repo = object : SpyListingRepo() {
            override suspend fun updateAbilityListingContribution(
                originalName: String,
                listing: Ability.Listing,
            ): ContributionResult = ContributionResult.Failure("An ability named \"NewName\" already exists")
        }
        val vm = editVm(editName = "OldName", repo = repo)
        vm.saveAbilityListing("NewName")

        val state = vm.saveState.first()
        assertTrue(state is AbilityListingContributionsViewModel.SaveState.Error)
    }

    @Test
    fun `requestDelete emits non-empty deleteImpact when builds reference the listing`() = runTest {
        val listing = Ability.Listing.of("Frost Wave")
        val repo = object : SpyListingRepo() {
            override suspend fun getAbilityListings() = listOf(listing)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("FrostMage"))
        }
        val vm = editVm(editName = "Frost Wave", repo = repo)
        // Wait for the init block to finish loading the edit listing
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()

        val impact = vm.deleteImpact.first()
        assertTrue(impact != null && !impact.isEmpty)
        assertEquals(listOf("FrostMage"), impact!!.referencingBuilds)
    }

    @Test
    fun `confirmDelete clears deleteImpact`() = runTest {
        val listing = Ability.Listing.of("Frost Wave")
        val repo = object : SpyListingRepo() {
            override suspend fun getAbilityListings() = listOf(listing)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("FrostMage"))
            override suspend fun deleteContribution(name: String) = ContributionResult.Success
        }
        val vm = editVm(editName = "Frost Wave", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        assertTrue(vm.deleteImpact.first() != null)

        vm.confirmDelete()
        assertNull(vm.deleteImpact.first())
    }

    @Test
    fun `cancelDelete clears deleteImpact without deleting`() = runTest {
        val deletedNames = mutableListOf<String>()
        val listing = Ability.Listing.of("Frost Wave")
        val repo = object : SpyListingRepo() {
            override suspend fun getAbilityListings() = listOf(listing)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("FrostMage"))
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Frost Wave", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        vm.cancelDelete()

        assertNull(vm.deleteImpact.first())
        assertTrue(deletedNames.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun editVm(
        editName: String,
        repo: SpyListingRepo,
    ): AbilityListingContributionsViewModel = AbilityListingContributionsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("name" to editName)),
        abilityListingRepository = repo,
        statusEffectRepository = NoopEffectRepo,
        ioDispatcher = dispatcher,
        shareUseCase = stubShareUseCase(),
    )

    private open class SpyListingRepo : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun updateAbilityListingContribution(
            originalName: String,
            listing: Ability.Listing,
        ): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }

    private object NoopEffectRepo : StatusEffectRepository {
        override val statusEffects: Flow<List<StatusEffect>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects(): List<StatusEffect> = emptyList()
        override suspend fun getContributions(): List<StatusEffect> = emptyList()
        override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult =
            ContributionResult.Failure("not used")
    }
}

private fun stubShareUseCase(): AbilityListingShareUseCase = AbilityListingShareUseCase(
    wireIo = WireIoRepository(
        essenceRepository = RenameTestStubEssenceRepo,
        awakeningStoneRepository = RenameTestStubStoneRepo,
        abilityListingRepository = RenameTestStubListingRepo,
        statusEffectRepository = RenameTestStubEffectRepo,
    ),
)

private object RenameTestStubEssenceRepo : wizardry.compendium.repositories.EssenceRepository {
    override val essences = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.domain.model.Essence>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.repositories.EssenceConflict>())
    override suspend fun getEssences() = emptyList<wizardry.compendium.domain.model.Essence>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.Essence>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.EssenceConflict>()
    override suspend fun saveManifestationContribution(manifestation: wizardry.compendium.domain.model.Essence.Manifestation) = ContributionResult.Success
    override suspend fun saveConfluenceContribution(confluence: wizardry.compendium.domain.model.Essence.Confluence, referencedManifestations: List<wizardry.compendium.domain.model.Essence.Manifestation>) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(target: wizardry.compendium.domain.model.Essence.Confluence, combination: wizardry.compendium.domain.model.ConfluenceSet) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: wizardry.compendium.domain.model.Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: wizardry.compendium.domain.model.Essence.Confluence) = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object RenameTestStubStoneRepo : wizardry.compendium.repositories.AwakeningStoneRepository {
    override val awakeningStones = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.domain.model.AwakeningStone>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.repositories.AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private object RenameTestStubListingRepo : AbilityListingRepository {
    override val abilityListings = kotlinx.coroutines.flow.flowOf(emptyList<Ability.Listing>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<AbilityListingConflict>())
    override suspend fun getAbilityListings() = emptyList<Ability.Listing>()
    override suspend fun getContributions() = emptyList<Ability.Listing>()
    override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
}

private object RenameTestStubEffectRepo : StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
}
