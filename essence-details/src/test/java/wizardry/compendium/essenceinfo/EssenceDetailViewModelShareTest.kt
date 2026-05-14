package wizardry.compendium.essenceinfo

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
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.share.EssenceShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceDetailViewModelShareTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `requestShare emits Encoded event with wire text`() = runTest(dispatcher) {
        val flame = Essence.of(name = "Flame", description = "fire", rarity = Rarity.Common, restricted = false)
        val repo = FakeEssenceRepo(listOf(flame))
        val useCase = object : EssenceShareUseCase(
            wireIo = stubWireIo(repo),
            essenceRepository = repo,
        ) {
            override fun encode(essence: Essence): String = "WIRE_BLOB"
        }
        val vm = EssenceDetailViewModel(
            essenceRepository = repo,
            ioDispatcher = dispatcher,
            shareUseCase = useCase,
        )
        vm.load("Flame")
        advanceUntilIdle()

        vm.shareEvents.test {
            vm.requestShare(flame)
            advanceUntilIdle()
            assertEquals(EssenceDetailViewModel.ShareEvent.Encoded("WIRE_BLOB"), awaitItem())
        }
    }

    private fun stubWireIo(essenceRepo: EssenceRepository): WireIoRepository = WireIoRepository(
        essenceRepository = essenceRepo,
        awakeningStoneRepository = StubAwakeningStoneRepo,
        abilityListingRepository = StubAbilityListingRepo,
        statusEffectRepository = StubStatusEffectRepo,
    )
}

private class FakeEssenceRepo(initial: List<Essence>) : EssenceRepository {
    private val flow = MutableStateFlow(initial)
    override val essences: Flow<List<Essence>> = flow
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = flow.value
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
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence) =
        ContributionResult.Success
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
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
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
