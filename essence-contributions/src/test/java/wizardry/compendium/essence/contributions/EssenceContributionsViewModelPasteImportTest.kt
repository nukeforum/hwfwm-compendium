package wizardry.compendium.essence.contributions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.share.ConfluenceImportPreview
import wizardry.compendium.share.PreviewCombination
import wizardry.compendium.share.PreviewEssence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceContributionsViewModelPasteImportTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var essenceRepo: FakeEssenceRepository
    private lateinit var stoneRepo: FakeAwakeningStoneRepository
    private lateinit var listingRepo: FakeAbilityListingRepository
    private lateinit var viewModel: EssenceContributionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = FakeEssenceRepository()
        stoneRepo = FakeAwakeningStoneRepository()
        listingRepo = FakeAbilityListingRepository()
        viewModel = EssenceContributionsViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            essenceRepository = essenceRepo,
            awakeningStoneRepository = stoneRepo,
            abilityListingRepository = listingRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun samplePreview(envelope: Envelope, confluenceName: String = "Doom") =
        ConfluenceImportPreview(
            envelope = envelope,
            confluenceName = confluenceName,
            isRestricted = false,
            combinations = listOf(PreviewCombination("Wind", "Blood", "Sin", false)),
            essences = listOf(
                PreviewEssence("Wind", Rarity.Common, true),
                PreviewEssence("Blood", Rarity.Uncommon, true),
                PreviewEssence("Sin", Rarity.Legendary, true),
            ),
            unresolvableNames = emptySet(),
        )

    @Test
    fun `startPasteImport transitions Idle to Reviewing`() = runTest(dispatcher) {
        val preview = samplePreview(Envelope(version = EnvelopeCodec.CurrentVersion))

        viewModel.startPasteImport(preview)
        advanceUntilIdle()

        assertEquals(
            EssenceContributionsViewModel.PasteImportState.Reviewing(preview),
            viewModel.pasteImportState.value,
        )
    }

    @Test
    fun `cancelPasteImport returns to Idle`() = runTest(dispatcher) {
        val preview = samplePreview(Envelope(version = EnvelopeCodec.CurrentVersion))
        viewModel.startPasteImport(preview)
        viewModel.cancelPasteImport()
        advanceUntilIdle()
        assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
    }

    @Test
    fun `confirmPasteImport runs through Saving to Done with summary`() = runTest(dispatcher) {
        val confluence = Essence.of(
            name = "Doom",
            restricted = false,
            ConfluenceSet(
                Essence.of("Wind", "", Rarity.Common, false),
                Essence.of("Blood", "", Rarity.Uncommon, false),
                Essence.of("Sin", "", Rarity.Legendary, false),
            ),
        )
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
        val envelope = exporter.exportSingle(confluence)
        val preview = samplePreview(envelope)

        viewModel.startPasteImport(preview)
        viewModel.confirmPasteImport()
        advanceUntilIdle()

        val state = viewModel.pasteImportState.value
        assertTrue(state is EssenceContributionsViewModel.PasteImportState.Done)
        val done = state as EssenceContributionsViewModel.PasteImportState.Done
        assertEquals("Doom", done.confluenceName)
        val addedNames = done.summary.added.map { it.name }
        assertTrue(addedNames.containsAll(listOf("Wind", "Blood", "Sin", "Doom")))
    }

    @Test
    fun `consumePasteImportTerminal clears Done back to Idle`() = runTest(dispatcher) {
        val confluence = Essence.of(
            name = "Doom",
            restricted = false,
            ConfluenceSet(
                Essence.of("Wind", "", Rarity.Common, false),
                Essence.of("Blood", "", Rarity.Uncommon, false),
                Essence.of("Sin", "", Rarity.Legendary, false),
            ),
        )
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
        val envelope = exporter.exportSingle(confluence)
        val preview = samplePreview(envelope)
        viewModel.startPasteImport(preview)
        viewModel.confirmPasteImport()
        advanceUntilIdle()
        viewModel.consumePasteImportTerminal()
        assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
    }

    @Test
    fun `confirmPasteImport is a no-op outside Reviewing`() = runTest(dispatcher) {
        viewModel.confirmPasteImport()
        advanceUntilIdle()
        assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
    }

    @Test
    fun `confirmPasteImport transitions to Failed when importer throws`() = runTest(dispatcher) {
        // WireImporter wraps each per-entry step in try/catch but only
        // catches WireDecodeException, not generic RuntimeException. So a
        // fake repository that throws RuntimeException from
        // saveManifestationContribution will bubble out of
        // wireImporter.import(...) and trip the runCatching in the VM,
        // driving the Failed branch.
        val throwingRepo = ThrowingFakeEssenceRepository()
        viewModel = EssenceContributionsViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            essenceRepository = throwingRepo,
            awakeningStoneRepository = stoneRepo,
            abilityListingRepository = listingRepo,
        )
        // Build an envelope that contains a manifestation so the throwing
        // saveManifestationContribution path is actually reached.
        val manifestation = Essence.of("Wind", "", Rarity.Common, false)
        val envelope = WireExporter(essenceRepo, stoneRepo, listingRepo)
            .exportSingle(manifestation)
        val preview = samplePreview(envelope)

        viewModel.startPasteImport(preview)
        viewModel.confirmPasteImport()
        advanceUntilIdle()

        val state = viewModel.pasteImportState.value
        assertTrue("expected Failed, got $state", state is EssenceContributionsViewModel.PasteImportState.Failed)
        val failed = state as EssenceContributionsViewModel.PasteImportState.Failed
        assertTrue(
            "reason should mention import failure, got '${failed.reason}'",
            failed.reason.startsWith("Import failed:"),
        )
    }
}

private class FakeEssenceRepository(
    canonical: List<Essence> = emptyList(),
    contributions: List<Essence> = emptyList(),
) : EssenceRepository {
    private val initial: List<Essence> = canonical + contributions
    val savedManifestations = mutableListOf<Essence.Manifestation>()
    val savedConfluences = mutableListOf<Essence.Confluence>()

    private val all: List<Essence>
        get() = initial + savedManifestations + savedConfluences

    override val essences: Flow<List<Essence>> = MutableStateFlow(initial)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = all
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult {
        savedManifestations += manifestation
        return ContributionResult.Success
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult {
        savedConfluences += confluence
        return ContributionResult.Success
    }
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
}

private class ThrowingFakeEssenceRepository : EssenceRepository {
    // Mirrors FakeEssenceRepository for everything except
    // saveManifestationContribution, which throws to drive the importer's
    // exception path out through the VM's runCatching.
    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult {
        throw RuntimeException("boom")
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
}

private class FakeAwakeningStoneRepository : AwakeningStoneRepository {
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
    override suspend fun getContributions(): List<AwakeningStone> = emptyList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult = ContributionResult.Success
}

private class FakeAbilityListingRepository : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
}
