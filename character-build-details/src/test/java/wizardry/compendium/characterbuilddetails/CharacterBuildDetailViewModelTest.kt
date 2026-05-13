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
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load by name emits Success`() = runTest {
        val repo = FakeRepo(listOf(build("Jason"), build("Humphrey")))
        val vm = CharacterBuildDetailViewModel(repo, FakeStatusEffectRepo(), exporter(), dispatcher)

        vm.load("Humphrey")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Success } as CharacterBuildDetailUiState.Success
        assertEquals("Humphrey", state.build.name)
    }

    @Test
    fun `load by unknown name emits Error`() = runTest {
        val vm = CharacterBuildDetailViewModel(FakeRepo(emptyList()), FakeStatusEffectRepo(), exporter(), dispatcher)

        vm.load("ghost")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Error }
        assertTrue(state is CharacterBuildDetailUiState.Error)
    }

    @Test
    fun `flow update refreshes the loaded build`() = runTest {
        val repo = FakeRepo(listOf(build("Jason", race = "Outworlder")))
        val vm = CharacterBuildDetailViewModel(repo, FakeStatusEffectRepo(), exporter(), dispatcher)

        vm.load("Jason")
        advanceUntilIdle()

        repo.update(listOf(build("Jason", race = "Earthling")))
        advanceUntilIdle()

        val state = vm.state.first { (it as? CharacterBuildDetailUiState.Success)?.build?.race == "Earthling" }
        assertEquals("Earthling", (state as CharacterBuildDetailUiState.Success).build.race)
    }

    private fun build(name: String, race: String = "Race"): CharacterBuild =
        CharacterBuild(name = name, race = race, racialAbilities = emptyList())

    private fun exporter(): WireExporter = WireExporter(
        essenceRepository = StubEssenceRepo(),
        awakeningStoneRepository = StubAwakeningStoneRepo(),
        abilityListingRepository = StubAbilityListingRepo(),
        statusEffectRepository = FakeStatusEffectRepo(),
    )

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
            manifestation: Essence.Manifestation,
        ): ContributionResult = ContributionResult.Success
        override suspend fun updateConfluenceContribution(
            confluence: Essence.Confluence,
        ): ContributionResult = ContributionResult.Success
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
        override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
            ContributionResult.Success
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
        override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            ContributionResult.Success
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
        override suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult = ContributionResult.Success
    }
}
