package wizardry.compendium.characterbuilddetails

import app.cash.turbine.test
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
import org.junit.Before
import org.junit.Test
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
import wizardry.compendium.ability.preview.AbilityTextRenderer
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.CharacterBuildShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository
import wizardry.compendium.wire.share.BuildShareDecoder

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildDetailViewModelShareTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `requestShareAsFile emits Encoded event with wire text`() = runTest(dispatcher) {
        val build = CharacterBuild(name = "Hero", race = "Elf", racialAbilities = emptyList())
        val useCase = object : CharacterBuildShareUseCase(
            wireIo = stubWireIo(),
            buildShareDecoder = stubBuildShareDecoder(),
        ) {
            override fun encode(build: CharacterBuild): String = "WIRE_BLOB"
        }
        val vm = newVm(useCase, build)
        vm.load("Hero")
        advanceUntilIdle()

        vm.shareEvents.test {
            vm.requestShareAsFile()
            advanceUntilIdle()
            assertEquals(
                CharacterBuildDetailViewModel.ShareEvent.Encoded(
                    text = "WIRE_BLOB",
                    title = "Export Hero build",
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `requestShareAsText emits EncodedAsText with renderer output`() = runTest(dispatcher) {
        val build = CharacterBuild(name = "Hero", race = "Elf", racialAbilities = emptyList())
        val useCase = CharacterBuildShareUseCase(
            wireIo = stubWireIo(),
            buildShareDecoder = stubBuildShareDecoder(),
        )
        val vm = newVm(useCase, build)
        vm.load("Hero")
        advanceUntilIdle()

        vm.shareEvents.test {
            vm.requestShareAsText()
            advanceUntilIdle()
            val expected = AbilityTextRenderer.renderBuild(build, emptyList())
            assertEquals(
                CharacterBuildDetailViewModel.ShareEvent.EncodedAsText(
                    text = expected,
                    title = "Share Hero build",
                ),
                awaitItem(),
            )
        }
    }

    private fun newVm(useCase: CharacterBuildShareUseCase, build: CharacterBuild) =
        CharacterBuildDetailViewModel(
            repository = FakeBuildRepo(listOf(build)),
            statusEffectRepository = FakeStatusEffectRepo(),
            shareUseCase = useCase,
            ioDispatcher = dispatcher,
        )

    private fun stubWireIo(): WireIoRepository = WireIoRepository(
        essenceRepository = StubEssenceRepo,
        awakeningStoneRepository = StubAwakeningStoneRepo,
        abilityListingRepository = StubAbilityListingRepo,
        statusEffectRepository = StubStatusEffectRepo,
    )

    private fun stubBuildShareDecoder(): BuildShareDecoder = BuildShareDecoder(
        essenceRepository = StubEssenceRepo,
        abilityListingRepository = StubAbilityListingRepo,
        buildRepository = StubBuildRepo,
    )

    private class FakeBuildRepo(initial: List<CharacterBuild>) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds() = flow.value
        override suspend fun getBuild(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild) = ContributionResult.Success
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
    }

    private class FakeStatusEffectRepo : StatusEffectRepository {
        override val statusEffects: Flow<List<StatusEffect>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects(): List<StatusEffect> = emptyList()
        override suspend fun getContributions(): List<StatusEffect> = emptyList()
        override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult = ContributionResult.Success
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult = ContributionResult.Success
    }
}

private object StubBuildRepo : CharacterBuildRepository {
    override val builds = flowOf(emptyList<CharacterBuild>())
    override suspend fun getBuilds() = emptyList<CharacterBuild>()
    override suspend fun getBuild(name: String): CharacterBuild? = null
    override suspend fun saveBuildContribution(build: CharacterBuild) = ContributionResult.Success
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
}

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
        combination: ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence) = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
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
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
}

private object StubAbilityListingRepo : AbilityListingRepository {
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
