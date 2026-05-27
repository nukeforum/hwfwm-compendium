package wizardry.compendium.statuseffect.contributions

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
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.share.StatusEffectShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class StatusEffectContributionsViewModelRenameTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun effect(name: String) = StatusEffect(
        name = name, type = StatusType.Affliction.Curse,
        properties = emptyList(), stackable = false, description = "",
    )

    @Test
    fun `editing calls updateStatusEffectContribution with original editName`() = runTest {
        val capturedCalls = mutableListOf<Pair<String, String>>()
        val repo = object : SpyEffectRepo() {
            override suspend fun updateStatusEffectContribution(
                originalName: String,
                effect: StatusEffect,
            ): ContributionResult {
                capturedCalls += originalName to effect.name
                return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Burn", repo = repo)
        vm.save("Inferno", StatusType.Affliction.Elemental, emptyList(), stackable = false, description = "")

        assertEquals(1, capturedCalls.size)
        assertEquals("Burn", capturedCalls.single().first)
        assertEquals("Inferno", capturedCalls.single().second)
    }

    @Test
    fun `editing with name collision emits SaveState Error`() = runTest {
        val repo = object : SpyEffectRepo() {
            override suspend fun updateStatusEffectContribution(
                originalName: String,
                effect: StatusEffect,
            ): ContributionResult =
                ContributionResult.Failure("A status effect named \"Inferno\" already exists")
        }
        val vm = editVm(editName = "Burn", repo = repo)
        vm.save("Inferno", StatusType.Affliction.Elemental, emptyList(), stackable = false, description = "")

        val state = vm.saveState.first()
        assertTrue(state is StatusEffectContributionsViewModel.SaveState.Error)
    }

    @Test
    fun `requestDelete with references shows deleteImpact`() = runTest {
        val impact = DeleteImpact(referencingAbilityListings = listOf("Pyro"))
        val repo = object : SpyEffectRepo() {
            override suspend fun getStatusEffects() = listOf(effect("Burn"))
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) = impact
        }
        val vm = editVm(editName = "Burn", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        val observed = vm.deleteImpact.first()
        assertEquals(impact, observed)
    }

    @Test
    fun `requestDelete with empty impact deletes immediately without surfacing dialog state`() = runTest {
        val deletedNames = mutableListOf<String>()
        val repo = object : SpyEffectRepo() {
            override suspend fun getStatusEffects() = listOf(effect("Burn"))
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name; return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Burn", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        dispatcher.scheduler.advanceUntilIdle()

        // Empty impact -> requestDelete proceeds straight to deleteContribution.
        // The VM never publishes a non-null deleteImpact, so no dialog ever appears.
        assertNull(vm.deleteImpact.first())
        assertEquals(listOf("Burn"), deletedNames)
    }

    @Test
    fun `cancelDelete clears deleteImpact without deleting`() = runTest {
        val deletedNames = mutableListOf<String>()
        val repo = object : SpyEffectRepo() {
            override suspend fun getStatusEffects() = listOf(effect("Burn"))
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) = DeleteImpact(referencingAbilityListings = listOf("Pyro"))
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name; return ContributionResult.Success
            }
        }
        val vm = editVm(editName = "Burn", repo = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete()
        vm.cancelDelete()

        assertNull(vm.deleteImpact.first())
        assertTrue(deletedNames.isEmpty())
    }

    // ─────────────────────────── helpers ────────────────────────────────────

    private fun editVm(
        editName: String,
        repo: SpyEffectRepo,
    ): StatusEffectContributionsViewModel = StatusEffectContributionsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("name" to editName)),
        repository = repo,
        shareUseCase = stubShareUseCase(),
        ioDispatcher = dispatcher,
    )

    private open class SpyEffectRepo : StatusEffectRepository {
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
        override suspend fun updateStatusEffectContribution(
            originalName: String,
            effect: StatusEffect,
        ): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }
}

private fun stubShareUseCase(): StatusEffectShareUseCase = StatusEffectShareUseCase(
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

private object RenameStubStoneRepo : wizardry.compendium.repositories.AwakeningStoneRepository {
    override val awakeningStones = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.domain.model.AwakeningStone>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.repositories.AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
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

private object RenameStubEffectRepo : StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = DeleteImpact()
}
