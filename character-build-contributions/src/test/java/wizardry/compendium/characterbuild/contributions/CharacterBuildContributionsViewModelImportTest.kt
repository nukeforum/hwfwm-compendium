package wizardry.compendium.characterbuild.contributions

import androidx.lifecycle.SavedStateHandle
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
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.BuildAbility
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.share.AttributePreview
import wizardry.compendium.wire.share.BuildImportPreview
import wizardry.compendium.wire.share.BuildShareDecoder
import wizardry.compendium.wire.share.RefResolution
import wizardry.compendium.wire.share.SlotResolution

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildContributionsViewModelImportTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `failed decoder result transitions PasteImportState to Failed`() = runTest {
        val buildRepo = FakeBuildRepo()
        val decoder = FakeDecoder(BuildShareDecoder.Result.Failed("Paste is empty."))
        val vm = create(buildRepo = buildRepo, decoder = decoder)
        advanceUntilIdle()

        vm.startImportFromText("bad")
        advanceUntilIdle()

        val state = vm.pasteImportState.value
        assertTrue(
            "expected Failed, got $state",
            state is CharacterBuildContributionsViewModel.PasteImportState.Failed,
        )
        assertEquals(
            "Paste is empty.",
            (state as CharacterBuildContributionsViewModel.PasteImportState.Failed).reason,
        )
    }

    @Test
    fun `confirmImport with all-resolved materializes build with rank tier progress`() = runTest {
        val wind = manifestation("Wind")
        val frostBolt = Ability.Listing(name = "Frost Bolt", effects = emptyList())
        val preview = BuildImportPreview(
            envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
            originalName = "Frosty",
            race = "Human",
            nameCollides = false,
            racials = emptyList(),
            attributes = listOf(
                AttributePreview(
                    slot = "Power",
                    resolution = SlotResolution.Resolved(
                        essence = wind,
                        abilities = listOf(RefResolution.Resolved("Frost Bolt", frostBolt)),
                        wireAbilities = listOf(BuildAbility("Frost Bolt", Rank.Bronze.ordinal, 2, 0.5f)),
                    ),
                ),
                AttributePreview("Speed", SlotResolution.Empty),
                AttributePreview("Spirit", SlotResolution.Empty),
                AttributePreview("Recovery", SlotResolution.Empty),
            ),
        )

        val buildRepo = FakeBuildRepo()
        val decoder = FakeDecoder(BuildShareDecoder.Result.Loaded(preview))
        val vm = create(buildRepo = buildRepo, decoder = decoder)
        advanceUntilIdle()

        vm.startImportFromText("ok")
        advanceUntilIdle()
        vm.confirmImport()
        advanceUntilIdle()

        val saved = buildRepo.allBuilds().singleOrNull()
            ?: error("expected exactly one saved build, got ${buildRepo.allBuilds()}")
        assertEquals("Frosty", saved.name)
        val powerEssence = saved.Power.essence
            ?: error("Power slot should be populated")
        assertEquals("Wind", powerEssence.essence.name)
        val ability = powerEssence.abilities.singleOrNull()
            ?: error("expected one ability, got ${powerEssence.abilities}")
        assertEquals("Frost Bolt", ability.name)
        assertEquals(Rank.Bronze, ability.rank)
        assertEquals(2, ability.tier)
        assertEquals(0.5f, ability.progress, 0.0f)
        assertEquals(
            CharacterBuildContributionsViewModel.PasteImportState.Done,
            vm.pasteImportState.value,
        )
    }

    @Test
    fun `confirmImport with name collision after rename uses new name`() = runTest {
        val preview = BuildImportPreview(
            envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
            originalName = "Frosty",
            race = "Human",
            nameCollides = true,
            racials = emptyList(),
            attributes = listOf(
                AttributePreview("Power", SlotResolution.Empty),
                AttributePreview("Speed", SlotResolution.Empty),
                AttributePreview("Spirit", SlotResolution.Empty),
                AttributePreview("Recovery", SlotResolution.Empty),
            ),
        )
        // Existing build that would collide with the original name.
        val existing = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())
        val buildRepo = FakeBuildRepo(listOf(existing))
        val decoder = FakeDecoder(BuildShareDecoder.Result.Loaded(preview))
        val vm = create(buildRepo = buildRepo, decoder = decoder)
        advanceUntilIdle()

        vm.startImportFromText("ok")
        advanceUntilIdle()
        vm.updateImportName("Frosty 2")
        vm.confirmImport()
        advanceUntilIdle()

        // The original "Frosty" build is still there, plus the renamed import.
        val names = buildRepo.allBuilds().map { it.name }.toSet()
        assertEquals(setOf("Frosty", "Frosty 2"), names)
        assertEquals(
            CharacterBuildContributionsViewModel.PasteImportState.Done,
            vm.pasteImportState.value,
        )
    }

    @Test
    fun `confirmImport with blank name transitions to Failed`() = runTest {
        val preview = BuildImportPreview(
            envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
            originalName = "Frosty",
            race = "Human",
            nameCollides = false,
            racials = emptyList(),
            attributes = listOf(
                AttributePreview("Power", SlotResolution.Empty),
                AttributePreview("Speed", SlotResolution.Empty),
                AttributePreview("Spirit", SlotResolution.Empty),
                AttributePreview("Recovery", SlotResolution.Empty),
            ),
        )
        val buildRepo = FakeBuildRepo()
        val decoder = FakeDecoder(BuildShareDecoder.Result.Loaded(preview))
        val vm = create(buildRepo = buildRepo, decoder = decoder)
        advanceUntilIdle()

        vm.startImportFromText("ok")
        advanceUntilIdle()
        vm.updateImportName("   ")
        vm.confirmImport()
        advanceUntilIdle()

        val state = vm.pasteImportState.value
        assertTrue(
            "expected Failed, got $state",
            state is CharacterBuildContributionsViewModel.PasteImportState.Failed,
        )
        assertTrue(buildRepo.allBuilds().isEmpty())
    }

    @Test
    fun `cancelImport returns state to Idle`() = runTest {
        val preview = BuildImportPreview(
            envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
            originalName = "Frosty",
            race = "Human",
            nameCollides = false,
            racials = emptyList(),
            attributes = listOf(
                AttributePreview("Power", SlotResolution.Empty),
                AttributePreview("Speed", SlotResolution.Empty),
                AttributePreview("Spirit", SlotResolution.Empty),
                AttributePreview("Recovery", SlotResolution.Empty),
            ),
        )
        val decoder = FakeDecoder(BuildShareDecoder.Result.Loaded(preview))
        val vm = create(decoder = decoder)
        advanceUntilIdle()

        vm.startImportFromText("ok")
        advanceUntilIdle()
        vm.cancelImport()

        assertEquals(
            CharacterBuildContributionsViewModel.PasteImportState.Idle,
            vm.pasteImportState.value,
        )
    }

    // --- helpers -------------------------------------------------------

    private fun create(
        buildRepo: FakeBuildRepo = FakeBuildRepo(),
        decoder: BuildShareDecoder = FakeDecoder(BuildShareDecoder.Result.Failed("unused")),
    ): CharacterBuildContributionsViewModel = CharacterBuildContributionsViewModel(
        savedStateHandle = SavedStateHandle(),
        buildRepository = buildRepo,
        essenceRepository = FakeEssenceRepo(),
        abilityListingRepository = FakeAbilityListingRepo(),
        buildShareDecoder = decoder,
    )

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name, rank = Rank.Iron, rarity = Rarity.Common,
        properties = emptyList(), description = "", isRestricted = false,
    )

    // The decoder is `open` for test substitution; we never invoke the real
    // wire decode in these tests, so the super-class repos are stubs.
    private class FakeDecoder(private val result: BuildShareDecoder.Result) : BuildShareDecoder(
        essenceRepository = FakeEssenceRepo(),
        abilityListingRepository = FakeAbilityListingRepo(),
        buildRepository = FakeBuildRepo(),
    ) {
        override suspend fun decode(text: String) = result
    }

    private class FakeBuildRepo(initial: List<CharacterBuild> = emptyList()) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds() = flow.value
        override suspend fun getBuild(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult {
            flow.value = flow.value.filterNot { it.name == build.name } + build
            return ContributionResult.Success
        }
        override suspend fun deleteContribution(name: String): ContributionResult {
            if (flow.value.none { it.name == name }) return ContributionResult.Failure("nope")
            flow.value = flow.value.filterNot { it.name == name }
            return ContributionResult.Success
        }
        fun allBuilds(): List<CharacterBuild> = flow.value
    }

    private class FakeEssenceRepo : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences(): List<Essence> = emptyList()
        override suspend fun getContributions(): List<Essence> = emptyList()
        override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>) = ContributionResult.Success
        override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet) = ContributionResult.Success
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
        override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
    }

    private class FakeAbilityListingRepo : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    }
}
