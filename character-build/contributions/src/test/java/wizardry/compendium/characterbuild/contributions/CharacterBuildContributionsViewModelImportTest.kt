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
import kotlinx.coroutines.flow.flowOf
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.CharacterBuildShareUseCase
import wizardry.compendium.share.DecodedSingle
import wizardry.compendium.wire.BuildAbility
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.repo.WireIoRepository
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
        val useCase = fakeUseCase(DecodedSingle.Failed("Paste is empty."))
        val vm = create(buildRepo = buildRepo, useCase = useCase)
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
        val useCase = fakeUseCase(DecodedSingle.Loaded(preview))
        val vm = create(buildRepo = buildRepo, useCase = useCase)
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
        val useCase = fakeUseCase(DecodedSingle.Loaded(preview))
        val vm = create(buildRepo = buildRepo, useCase = useCase)
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
        val useCase = fakeUseCase(DecodedSingle.Loaded(preview))
        val vm = create(buildRepo = buildRepo, useCase = useCase)
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
        val useCase = fakeUseCase(DecodedSingle.Loaded(preview))
        val vm = create(useCase = useCase)
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
        useCase: CharacterBuildShareUseCase = fakeUseCase(DecodedSingle.Failed("unused")),
    ): CharacterBuildContributionsViewModel = CharacterBuildContributionsViewModel(
        savedStateHandle = SavedStateHandle(),
        buildRepository = buildRepo,
        essenceRepository = FakeEssenceRepo(),
        abilityListingRepository = FakeAbilityListingRepo(),
        shareUseCase = useCase,
    )

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name, rank = Rank.Iron, rarity = Rarity.Common,
        properties = emptyList(), description = "", isRestricted = false,
    )

    // The use case is `open` for test substitution; we never invoke the real
    // wire decode in these tests, so the super-class deps are stubs.
    private fun fakeUseCase(result: DecodedSingle<BuildImportPreview>): CharacterBuildShareUseCase {
        val essenceRepo = FakeEssenceRepo()
        val listingRepo = FakeAbilityListingRepo()
        val buildRepo = FakeBuildRepo()
        val decoder = BuildShareDecoder(
            essenceRepository = essenceRepo,
            abilityListingRepository = listingRepo,
            buildRepository = buildRepo,
        )
        val wireIo = WireIoRepository(
            essenceRepository = essenceRepo,
            awakeningStoneRepository = StubAwakeningStoneRepo,
            abilityListingRepository = listingRepo,
            statusEffectRepository = StubStatusEffectRepo,
        )
        return object : CharacterBuildShareUseCase(wireIo = wireIo, buildShareDecoder = decoder) {
            override suspend fun decodeBuildBundle(text: String): DecodedSingle<BuildImportPreview> = result
        }
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
        override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
        override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
        override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
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
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }
}

private object StubAwakeningStoneRepo : AwakeningStoneRepository {
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

private object StubStatusEffectRepo : StatusEffectRepository {
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
