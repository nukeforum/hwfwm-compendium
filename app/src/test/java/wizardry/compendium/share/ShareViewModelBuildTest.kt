package wizardry.compendium.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelBuildTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var essenceRepo: SvbEssenceRepo
    private lateinit var stoneRepo: SvbStoneRepo
    private lateinit var listingRepo: SvbListingRepo
    private lateinit var statusRepo: SvbStatusEffectRepo
    private lateinit var buildRepo: SvbBuildRepo
    private lateinit var decoder: BuildShareDecoder
    private lateinit var viewModel: ShareViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = SvbEssenceRepo()
        stoneRepo = SvbStoneRepo()
        listingRepo = SvbListingRepo()
        statusRepo = SvbStatusEffectRepo()
        buildRepo = SvbBuildRepo()
        decoder = BuildShareDecoder(essenceRepo, listingRepo, buildRepo)
        viewModel = ShareViewModel(essenceRepo, stoneRepo, listingRepo, statusRepo, decoder)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `encodeBuild then decodeBuildBundle round-trips`() = runTest(dispatcher) {
        val build = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())
        val encoded = viewModel.encodeBuild(build)
        val result = viewModel.decodeBuildBundle(encoded)
        assertTrue(result is ShareViewModel.DecodedSingle.Loaded)
        val preview = (result as ShareViewModel.DecodedSingle.Loaded).model
        assertEquals("Frosty", preview.originalName)
        assertEquals("Human", preview.race)
    }

    @Test
    fun `renderBuildAsText returns plain-text rendering`() {
        val build = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())
        val text = viewModel.renderBuildAsText(build, statusEffects = emptyList())
        assertTrue(text.startsWith("Frosty\nHuman\n"))
    }
}

private class SvbEssenceRepo : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
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

private class SvbStoneRepo : AwakeningStoneRepository {
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

private class SvbListingRepo : AbilityListingRepository {
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

private class SvbStatusEffectRepo : StatusEffectRepository {
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

private class SvbBuildRepo : CharacterBuildRepository {
    override val builds: Flow<List<CharacterBuild>> = MutableStateFlow(emptyList())
    override suspend fun getBuilds(): List<CharacterBuild> = emptyList()
    override suspend fun getBuild(name: String): CharacterBuild? = null
    override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult = ContributionResult.Success
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
}
