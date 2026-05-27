package wizardry.compendium.essence.contributions

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
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.EssenceShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceContributionsViewModelRenameTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm(
        editName: String?,
        repo: EssenceRepository,
    ): EssenceContributionsViewModel {
        val savedState = if (editName != null) SavedStateHandle(mapOf("name" to editName))
        else SavedStateHandle()
        return EssenceContributionsViewModel(
            savedStateHandle = savedState,
            essenceRepository = repo,
            awakeningStoneRepository = NoopStoneRepo,
            abilityListingRepository = NoopListingRepo,
            statusEffectRepository = NoopEffectRepo,
            ioDispatcher = dispatcher,
            shareUseCase = stubShareUseCase(),
        )
    }

    @Test
    fun `editing changes the manifestation name — updateManifestationContribution called with originalName`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val repo = object : SpyEssenceRepo() {
            override suspend fun updateManifestationContribution(
                originalName: String,
                manifestation: Essence.Manifestation,
            ): ContributionResult {
                calls += originalName to manifestation.name
                return ContributionResult.Success
            }
            override suspend fun getEssences() = listOf<Essence>(manifestation("OldName"))
            override suspend fun isContribution(name: String) = true
        }
        val vm = makeVm("OldName", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.saveManifestation("NewName", Rarity.Common, "desc", false)

        assertEquals(1, calls.size)
        assertEquals("OldName", calls.single().first)
        assertEquals("NewName", calls.single().second)
    }

    @Test
    fun `editing changes the confluence name — updateConfluenceContribution called with originalName`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val oldConfluence = Essence.of("OldConfluence", false, ConfluenceSet(a, b, c))
        val calls = mutableListOf<Pair<String, String>>()
        val repo = object : SpyEssenceRepo() {
            override suspend fun updateConfluenceContribution(
                originalName: String,
                confluence: Essence.Confluence,
            ): ContributionResult {
                calls += originalName to confluence.name
                return ContributionResult.Success
            }
            override suspend fun getEssences() = listOf<Essence>(a, b, c, oldConfluence)
            override suspend fun isContribution(name: String) = true
        }
        val vm = makeVm("OldConfluence", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.updateConfluence("NewConfluence", false)

        assertEquals(1, calls.size)
        assertEquals("OldConfluence", calls.single().first)
        assertEquals("NewConfluence", calls.single().second)
    }

    @Test
    fun `editing with collision emits SaveState Error`() = runTest {
        val repo = object : SpyEssenceRepo() {
            override suspend fun updateManifestationContribution(
                originalName: String,
                manifestation: Essence.Manifestation,
            ): ContributionResult = ContributionResult.Failure("An essence named \"NewName\" already exists")
            override suspend fun getEssences() = listOf<Essence>(manifestation("OldName"))
            override suspend fun isContribution(name: String) = true
        }
        val vm = makeVm("OldName", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.saveManifestation("NewName", Rarity.Common, "desc", false)

        val state = vm.saveState.first()
        assertTrue(state is EssenceContributionsViewModel.SaveState.Error)
    }

    @Test
    fun `requestDelete on contribution with referencing builds emits non-empty deleteImpact`() = runTest {
        val essence = manifestation("Wind")
        val repo = object : SpyEssenceRepo() {
            override suspend fun getEssences() = listOf<Essence>(essence)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("MyBuild"))
        }
        val vm = makeVm("Wind", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete()

        val impact = vm.deleteImpact.first()
        assertTrue(impact != null && !impact.isEmpty)
        assertEquals(listOf("MyBuild"), impact!!.referencingBuilds)
    }

    @Test
    fun `requestDelete on contribution referenced by confluence_sets emits referencingConfluenceSets`() = runTest {
        val essence = manifestation("Wind")
        val repo = object : SpyEssenceRepo() {
            override suspend fun getEssences() = listOf<Essence>(essence)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingConfluenceSets = listOf("Storm", "Tempest"))
        }
        val vm = makeVm("Wind", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete()

        val impact = vm.deleteImpact.first()
        assertEquals(listOf("Storm", "Tempest"), impact!!.referencingConfluenceSets)
    }

    @Test
    fun `confirmDelete clears deleteImpact`() = runTest {
        val essence = manifestation("Wind")
        val repo = object : SpyEssenceRepo() {
            override suspend fun getEssences() = listOf<Essence>(essence)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("MyBuild"))
            override suspend fun deleteContribution(name: String) = ContributionResult.Success
        }
        val vm = makeVm("Wind", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete()
        assertTrue(vm.deleteImpact.first() != null)

        vm.confirmDelete()
        assertNull(vm.deleteImpact.first())
    }

    @Test
    fun `cancelDelete clears deleteImpact without deleting`() = runTest {
        val deletedNames = mutableListOf<String>()
        val essence = manifestation("Wind")
        val repo = object : SpyEssenceRepo() {
            override suspend fun getEssences() = listOf<Essence>(essence)
            override suspend fun isContribution(name: String) = true
            override suspend fun checkDeleteImpact(name: String) =
                DeleteImpact(referencingBuilds = listOf("MyBuild"))
            override suspend fun deleteContribution(name: String): ContributionResult {
                deletedNames += name
                return ContributionResult.Success
            }
        }
        val vm = makeVm("Wind", repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDelete()
        vm.cancelDelete()

        assertNull(vm.deleteImpact.first())
        assertTrue(deletedNames.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    private open class SpyEssenceRepo : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences(): List<Essence> = emptyList()
        override suspend fun getContributions(): List<Essence> = emptyList()
        override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
        override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }

    private object NoopStoneRepo : AwakeningStoneRepository {
        override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
        override suspend fun getContributions(): List<AwakeningStone> = emptyList()
        override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
        override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
    }

    private object NoopListingRepo : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }

    private object NoopEffectRepo : StatusEffectRepository {
        override val statusEffects: Flow<List<StatusEffect>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects(): List<StatusEffect> = emptyList()
        override suspend fun getContributions(): List<StatusEffect> = emptyList()
        override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult = ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect): ContributionResult = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun stubShareUseCase(): EssenceShareUseCase = EssenceShareUseCase(
    wireIo = WireIoRepository(
        essenceRepository = VmTestStubEssenceRepo,
        awakeningStoneRepository = VmTestStubStoneRepo,
        abilityListingRepository = VmTestStubListingRepo,
        statusEffectRepository = VmTestStubEffectRepo,
    ),
    essenceRepository = VmTestStubEssenceRepo,
)

private object VmTestStubEssenceRepo : EssenceRepository {
    override val essences = kotlinx.coroutines.flow.flowOf(emptyList<Essence>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<EssenceConflict>())
    override suspend fun getEssences() = emptyList<Essence>()
    override suspend fun getContributions() = emptyList<Essence>()
    override suspend fun getConflicts() = emptyList<EssenceConflict>()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object VmTestStubStoneRepo : AwakeningStoneRepository {
    override val awakeningStones = kotlinx.coroutines.flow.flowOf(emptyList<AwakeningStone>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private object VmTestStubListingRepo : AbilityListingRepository {
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

private object VmTestStubEffectRepo : StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
}
