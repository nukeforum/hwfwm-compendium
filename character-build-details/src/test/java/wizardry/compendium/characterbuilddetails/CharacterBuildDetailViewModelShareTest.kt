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
class CharacterBuildDetailViewModelShareTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `shareText and encodeFile return non-empty strings for a loaded build`() = runTest {
        val build = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())
        val buildRepo = FakeBuildRepo(listOf(build))
        val statusEffectRepo = FakeStatusEffectRepo()
        val exporter = WireExporter(
            essenceRepository = FakeEssenceRepo(),
            awakeningStoneRepository = FakeAwakeningStoneRepo(),
            abilityListingRepository = FakeAbilityListingRepo(),
            statusEffectRepository = statusEffectRepo,
        )
        val vm = CharacterBuildDetailViewModel(buildRepo, statusEffectRepo, exporter)
            .also { it.ioDispatcher = dispatcher }

        vm.load("Frosty")
        advanceUntilIdle()

        val state = vm.state.first { it is CharacterBuildDetailUiState.Success } as CharacterBuildDetailUiState.Success
        assertEquals("Frosty", state.build.name)

        val shareText = vm.shareText()
        assertTrue("shareText starts with Frosty\\n", shareText.startsWith("Frosty\n"))

        val encoded = vm.encodeFile()
        assertTrue("encodeFile is non-empty", encoded.isNotEmpty())
    }

    @Test
    fun `shareText and encodeFile return empty when state is not Success`() = runTest {
        val exporter = WireExporter(
            essenceRepository = FakeEssenceRepo(),
            awakeningStoneRepository = FakeAwakeningStoneRepo(),
            abilityListingRepository = FakeAbilityListingRepo(),
            statusEffectRepository = FakeStatusEffectRepo(),
        )
        val vm = CharacterBuildDetailViewModel(FakeBuildRepo(emptyList()), FakeStatusEffectRepo(), exporter)
            .also { it.ioDispatcher = dispatcher }

        // No load() — state is Loading.
        assertEquals("", vm.shareText())
        assertEquals("", vm.encodeFile())
    }

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

    private class FakeEssenceRepo : EssenceRepository {
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

    private class FakeAwakeningStoneRepo : AwakeningStoneRepository {
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

    private class FakeAbilityListingRepo : AbilityListingRepository {
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
}
