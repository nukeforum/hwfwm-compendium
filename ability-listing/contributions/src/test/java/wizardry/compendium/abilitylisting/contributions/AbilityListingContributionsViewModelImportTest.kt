package wizardry.compendium.abilitylisting.contributions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.AbilityListingShareUseCase
import wizardry.compendium.share.DecodedSingle
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AbilityListingContributionsViewModelImportTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `requestImport pre-fills form and emits Loaded`() = runTest(dispatcher) {
        val fireball = Ability.Listing.of("Fireball")
        val useCase = object : AbilityListingShareUseCase(wireIo = stubWireIo()) {
            override fun decodeSingleAbility(text: String) = DecodedSingle.Loaded(fireball)
        }
        val vm = newVm(useCase)

        vm.importEvents.test {
            vm.requestImport("ignored")
            assertEquals(
                AbilityListingContributionsViewModel.ImportEvent.Loaded(fireball),
                awaitItem(),
            )
        }
        assertEquals("Fireball", vm.importedName.value)
    }

    @Test
    fun `requestImport emits Failed on rejected decode`() = runTest(dispatcher) {
        val useCase = object : AbilityListingShareUseCase(wireIo = stubWireIo()) {
            override fun decodeSingleAbility(text: String) = DecodedSingle.Failed("bad paste")
        }
        val vm = newVm(useCase)

        vm.importEvents.test {
            vm.requestImport("ignored")
            assertEquals(
                AbilityListingContributionsViewModel.ImportEvent.Failed("bad paste"),
                awaitItem(),
            )
        }
        assertEquals(null, vm.importedName.value)
    }

    private fun newVm(useCase: AbilityListingShareUseCase) = AbilityListingContributionsViewModel(
        savedStateHandle = SavedStateHandle(),
        abilityListingRepository = FakeListingRepo,
        statusEffectRepository = FakeEffectRepo,
        ioDispatcher = dispatcher,
        shareUseCase = useCase,
    )
}

private fun stubWireIo() = WireIoRepository(
    essenceRepository = StubEssenceRepo,
    awakeningStoneRepository = StubStoneRepo,
    abilityListingRepository = StubListingRepo,
    statusEffectRepository = StubEffectRepo,
)

private object StubEssenceRepo : EssenceRepository {
    override val essences = flowOf(emptyList<Essence>())
    override val conflicts = flowOf(emptyList<EssenceConflict>())
    override suspend fun getEssences() = emptyList<Essence>()
    override suspend fun getContributions() = emptyList<Essence>()
    override suspend fun getConflicts() = emptyList<EssenceConflict>()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: wizardry.compendium.domain.model.ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence) = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object StubStoneRepo : AwakeningStoneRepository {
    override val awakeningStones = flowOf(emptyList<AwakeningStone>())
    override val conflicts = flowOf(emptyList<AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
}

private object StubListingRepo : AbilityListingRepository {
    override val abilityListings = flowOf(emptyList<Ability.Listing>())
    override val conflicts = flowOf(emptyList<AbilityListingConflict>())
    override suspend fun getAbilityListings() = emptyList<Ability.Listing>()
    override suspend fun getContributions() = emptyList<Ability.Listing>()
    override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
}

private object StubEffectRepo : StatusEffectRepository {
    override val statusEffects = flowOf(emptyList<StatusEffect>())
    override val conflicts = flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
}

private object FakeListingRepo : AbilityListingRepository by StubListingRepo
private object FakeEffectRepo : StatusEffectRepository by StubEffectRepo
