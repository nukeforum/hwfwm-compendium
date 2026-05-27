package wizardry.compendium.awakeningstone.contributions

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
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.share.AwakeningStoneShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AwakeningStoneContributionsViewModelRenameTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `editing calls updateAwakeningStoneContribution with original editName`() = runTest {
        val capturedCalls = mutableListOf<Pair<String, String>>()
        val repo = object : SpyStoneRepo() {
            override suspend fun updateAwakeningStoneContribution(
                originalName: String,
                stone: AwakeningStone,
            ): ContributionResult {
                capturedCalls += originalName to stone.name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "OldName", repo = repo)
        vm.saveAwakeningStone("NewName", Rarity.Common)

        assertEquals(1, capturedCalls.size)
        assertEquals("OldName", capturedCalls.single().first)
        assertEquals("NewName", capturedCalls.single().second)
    }

    @Test
    fun `editing with name collision emits SaveState Error`() = runTest {
        val repo = object : SpyStoneRepo() {
            override suspend fun updateAwakeningStoneContribution(
                originalName: String,
                stone: AwakeningStone,
            ): ContributionResult = ContributionResult.Failure("An awakening stone named \"NewName\" already exists")
        }
        val vm = editVm(editName = "OldName", repo = repo)
        vm.saveAwakeningStone("NewName", Rarity.Common)

        val state = vm.saveState.first()
        assertTrue(state is AwakeningStoneContributionsViewModel.SaveState.Error)
    }

    @Test
    fun `requestDelete short-circuits to confirmDelete since impact is always empty`() = runTest {
        val stone = AwakeningStone.of("Granite", Rarity.Common)
        val deletedNames = mutableListOf<String>()
        val repo = object : SpyStoneRepo() {
            override suspend fun getAwakeningStones() = listOf(stone)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Granite", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        // deleteImpact is set to empty DeleteImpact() — screen's LaunchedEffect
        // would call confirmDelete(). Simulate that directly:
        val impact = vm.deleteImpact.first()
        assertTrue(impact != null && impact.isEmpty)

        vm.confirmDelete()
        assertNull(vm.deleteImpact.first())
        assertEquals(listOf("Granite"), deletedNames)
    }

    @Test
    fun `cancelDelete clears deleteImpact without deleting`() = runTest {
        val stone = AwakeningStone.of("Granite", Rarity.Common)
        val deletedNames = mutableListOf<String>()
        val repo = object : SpyStoneRepo() {
            override suspend fun getAwakeningStones() = listOf(stone)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Granite", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        vm.cancelDelete()

        assertNull(vm.deleteImpact.first())
        assertTrue(deletedNames.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private fun editVm(
        editName: String,
        repo: SpyStoneRepo,
    ): AwakeningStoneContributionsViewModel = AwakeningStoneContributionsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("name" to editName)),
        awakeningStoneRepository = repo,
        ioDispatcher = dispatcher,
        shareUseCase = stubShareUseCase(),
    )

    private open class SpyStoneRepo : AwakeningStoneRepository {
        override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
        override suspend fun getContributions(): List<AwakeningStone> = emptyList()
        override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
        override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun updateAwakeningStoneContribution(
            originalName: String,
            stone: AwakeningStone,
        ): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }
}

private fun stubShareUseCase(): AwakeningStoneShareUseCase = AwakeningStoneShareUseCase(
    wireIo = WireIoRepository(
        essenceRepository = RenameStubEssenceRepo,
        awakeningStoneRepository = RenameStubStoneRepo,
        abilityListingRepository = RenameStubListingRepo,
        statusEffectRepository = RenameStubEffectRepo,
    ),
)

private object RenameStubEssenceRepo : wizardry.compendium.repositories.EssenceRepository {
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
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object RenameStubStoneRepo : AwakeningStoneRepository {
    override val awakeningStones = kotlinx.coroutines.flow.flowOf(emptyList<AwakeningStone>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object RenameStubListingRepo : wizardry.compendium.repositories.AbilityListingRepository {
    override val abilityListings = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.domain.model.Ability.Listing>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.repositories.AbilityListingConflict>())
    override suspend fun getAbilityListings() = emptyList<wizardry.compendium.domain.model.Ability.Listing>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.Ability.Listing>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.AbilityListingConflict>()
    override suspend fun saveAbilityListingContribution(listing: wizardry.compendium.domain.model.Ability.Listing) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: wizardry.compendium.domain.model.Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
}

private object RenameStubEffectRepo : wizardry.compendium.repositories.StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.domain.model.StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.repositories.StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<wizardry.compendium.domain.model.StatusEffect>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.StatusEffect>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: wizardry.compendium.domain.model.StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(originalName: String, effect: wizardry.compendium.domain.model.StatusEffect) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
}
