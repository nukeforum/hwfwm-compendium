package wizardry.compendium.characterbuilddetails

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.share.CharacterBuildShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository
import wizardry.compendium.wire.share.BuildShareDecoder

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load by name emits Success`() = runTest {
        val repo = FakeRepo(listOf(build("Jason"), build("Humphrey")))
        val vm = CharacterBuildDetailViewModel(repo, FakeStatusEffectRepo(), shareUseCase(), dispatcher)

        vm.load("Humphrey")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Success } as CharacterBuildDetailUiState.Success
        assertEquals("Humphrey", state.build.name)
    }

    @Test
    fun `load by unknown name emits Error`() = runTest {
        val vm = CharacterBuildDetailViewModel(FakeRepo(emptyList()), FakeStatusEffectRepo(), shareUseCase(), dispatcher)

        vm.load("ghost")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Error }
        assertTrue(state is CharacterBuildDetailUiState.Error)
    }

    @Test
    fun `flow update refreshes the loaded build`() = runTest {
        val repo = FakeRepo(listOf(build("Jason", race = "Outworlder")))
        val vm = CharacterBuildDetailViewModel(repo, FakeStatusEffectRepo(), shareUseCase(), dispatcher)

        vm.load("Jason")
        advanceUntilIdle()

        repo.update(listOf(build("Jason", race = "Earthling")))
        advanceUntilIdle()

        val state = vm.state.first { (it as? CharacterBuildDetailUiState.Success)?.build?.race == "Earthling" }
        assertEquals("Earthling", (state as CharacterBuildDetailUiState.Success).build.race)
    }

    private fun build(name: String, race: String = "Race"): CharacterBuild =
        CharacterBuild(name = name, race = race, racialAbilities = emptyList())

    private fun shareUseCase(): CharacterBuildShareUseCase {
        val essenceRepo = StubEssenceRepo()
        val listingRepo = StubAbilityListingRepo()
        return CharacterBuildShareUseCase(
            wireIo = WireIoRepository(
                essenceRepository = essenceRepo,
                awakeningStoneRepository = StubAwakeningStoneRepo(),
                abilityListingRepository = listingRepo,
                statusEffectRepository = FakeStatusEffectRepo(),
            ),
            buildShareDecoder = BuildShareDecoder(
                essenceRepository = essenceRepo,
                abilityListingRepository = listingRepo,
                buildRepository = FakeRepo(emptyList()),
            ),
        )
    }

    private class StubEssenceRepo : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences(): List<Essence> = emptyList()
        override suspend fun getContributions(): List<Essence> = emptyList()
        override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
        override suspend fun saveManifestationContribution(
            manifestation: Essence.Manifestation,
        ): ContributionResult = ContributionResult.Success
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
        override suspend fun updateManifestationContribution(
            originalName: String,
            manifestation: Essence.Manifestation,
        ): ContributionResult = ContributionResult.Success
        override suspend fun updateConfluenceContribution(
            originalName: String,
            confluence: Essence.Confluence,
        ): ContributionResult = ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }

    private class StubAwakeningStoneRepo : AwakeningStoneRepository {
        override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
        override suspend fun getContributions(): List<AwakeningStone> = emptyList()
        override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
        override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
            ContributionResult.Success
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateAwakeningStoneContribution(originalName: String, stone: AwakeningStone): ContributionResult =
            ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
    }

    private class StubAbilityListingRepo : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            ContributionResult.Success
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
            ContributionResult.Success
        override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
    }

    private class FakeRepo(initial: List<CharacterBuild>) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds() = flow.value
        override suspend fun getBuild(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild) = ContributionResult.Success
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        fun update(next: List<CharacterBuild>) { flow.value = next }
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
        override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect): ContributionResult = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }
}
