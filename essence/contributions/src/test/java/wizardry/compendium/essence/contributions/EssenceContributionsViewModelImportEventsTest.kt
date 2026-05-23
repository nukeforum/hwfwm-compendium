package wizardry.compendium.essence.contributions

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.ConfluenceImportPreview
import wizardry.compendium.share.DecodedSingle
import wizardry.compendium.share.EssenceShareUseCase
import wizardry.compendium.share.PreviewCombination
import wizardry.compendium.share.PreviewEssence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceContributionsViewModelImportEventsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val flame = Essence.of(name = "Flame", description = "fire", rarity = Rarity.Common, restricted = false)
    private val samplePreview = ConfluenceImportPreview(
        envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
        confluenceName = "Doom",
        isRestricted = false,
        combinations = listOf(PreviewCombination("Wind", "Blood", "Sin", false)),
        essences = listOf(
            PreviewEssence("Wind", Rarity.Common, true),
            PreviewEssence("Blood", Rarity.Uncommon, true),
            PreviewEssence("Sin", Rarity.Legendary, true),
        ),
        unresolvableNames = emptySet(),
    )

    private fun newVm(useCase: EssenceShareUseCase): EssenceContributionsViewModel =
        EssenceContributionsViewModel(
            savedStateHandle = SavedStateHandle(),
            essenceRepository = StubEssenceRepoLocal,
            awakeningStoneRepository = StubStoneRepoLocal,
            abilityListingRepository = StubListingRepoLocal,
            statusEffectRepository = StubEffectRepoLocal,
            ioDispatcher = dispatcher,
            shareUseCase = useCase,
        )

    @Test
    fun `manifestation loaded emits ManifestationLoaded event`() = runTest(dispatcher) {
        val useCase = stubUseCase(
            manifestation = DecodedSingle.Loaded(flame),
        )
        val vm = newVm(useCase)

        vm.importEvents.test {
            vm.requestImportManifestation("anything")
            advanceUntilIdle()
            assertEquals(
                EssenceContributionsViewModel.ImportEvent.ManifestationLoaded(flame),
                awaitItem(),
            )
        }
    }

    @Test
    fun `manifestation failed emits Failed event`() = runTest(dispatcher) {
        val useCase = stubUseCase(
            manifestation = DecodedSingle.Failed("nope"),
        )
        val vm = newVm(useCase)

        vm.importEvents.test {
            vm.requestImportManifestation("anything")
            advanceUntilIdle()
            assertEquals(
                EssenceContributionsViewModel.ImportEvent.Failed("nope"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `confluence loaded advances pasteImportState to Reviewing without emitting an event`() = runTest(dispatcher) {
        val useCase = stubUseCase(
            confluence = DecodedSingle.Loaded(samplePreview),
        )
        val vm = newVm(useCase)

        vm.requestImportConfluence("anything")
        advanceUntilIdle()

        val state = vm.pasteImportState.value
        assertTrue("expected Reviewing, got $state", state is EssenceContributionsViewModel.PasteImportState.Reviewing)
        assertEquals(samplePreview, (state as EssenceContributionsViewModel.PasteImportState.Reviewing).preview)
    }

    @Test
    fun `confluence failed emits Failed event and leaves pasteImportState Idle`() = runTest(dispatcher) {
        val useCase = stubUseCase(
            confluence = DecodedSingle.Failed("bad"),
        )
        val vm = newVm(useCase)

        vm.importEvents.test {
            vm.requestImportConfluence("anything")
            advanceUntilIdle()
            assertEquals(
                EssenceContributionsViewModel.ImportEvent.Failed("bad"),
                awaitItem(),
            )
        }
        assertEquals(
            EssenceContributionsViewModel.PasteImportState.Idle,
            vm.pasteImportState.value,
        )
    }

    private fun stubUseCase(
        manifestation: DecodedSingle<Essence.Manifestation> = DecodedSingle.Failed("unused"),
        confluence: DecodedSingle<ConfluenceImportPreview> = DecodedSingle.Failed("unused"),
    ): EssenceShareUseCase = object : EssenceShareUseCase(
        wireIo = WireIoRepository(
            essenceRepository = StubEssenceRepoLocal,
            awakeningStoneRepository = StubStoneRepoLocal,
            abilityListingRepository = StubListingRepoLocal,
            statusEffectRepository = StubEffectRepoLocal,
        ),
        essenceRepository = StubEssenceRepoLocal,
    ) {
        override fun decodeSingleManifestation(text: String) = manifestation
        override suspend fun decodeConfluenceBundle(text: String) = confluence
    }
}

private object StubEssenceRepoLocal : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
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
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence) =
        ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object StubStoneRepoLocal : AwakeningStoneRepository {
    override val awakeningStones = flowOf(emptyList<AwakeningStone>())
    override val conflicts = flowOf(emptyList<AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private object StubListingRepoLocal : AbilityListingRepository {
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

private object StubEffectRepoLocal : StatusEffectRepository {
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
