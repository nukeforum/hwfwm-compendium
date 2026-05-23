package wizardry.compendium.statuseffect.details

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.share.StatusEffectShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

class StatusEffectDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setup() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun teardown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun effect(name: String) = StatusEffect(
        name = name, type = StatusType.Affliction.Curse,
        properties = emptyList(), stackable = false, description = "",
    )

    private class FakeRepo(
        private val items: List<StatusEffect>,
        private val contributions: Set<String> = emptySet(),
    ) : StatusEffectRepository {
        override val statusEffects = flowOf(items)
        override val conflicts = flowOf(emptyList<StatusEffectConflict>())
        override suspend fun getStatusEffects() = items
        override suspend fun getContributions() = items.filter { it.name in contributions }
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
        override suspend fun isContribution(name: String) = name in contributions
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    }

    @Test
    fun `load found effect emits Success with isContribution flag`() = runTest {
        val burn = effect("Burn")
        val vm = StatusEffectDetailViewModel(FakeRepo(listOf(burn), contributions = setOf("Burn")), stubShareUseCase())
        vm.load("Burn")
        val state = vm.state.value
        assertTrue(state is StatusEffectDetailUiState.Success)
        state as StatusEffectDetailUiState.Success
        assertEquals(burn, state.effect)
        assertTrue(state.isContribution)
    }

    @Test
    fun `load missing effect emits Error`() = runTest {
        val vm = StatusEffectDetailViewModel(FakeRepo(emptyList()), stubShareUseCase())
        vm.load("Nope")
        assertTrue(vm.state.value is StatusEffectDetailUiState.Error)
    }
}

private fun stubShareUseCase(): StatusEffectShareUseCase = StatusEffectShareUseCase(
    wireIo = WireIoRepository(
        essenceRepository = StubVmEssenceRepo,
        awakeningStoneRepository = StubVmStoneRepo,
        abilityListingRepository = StubVmListingRepo,
        statusEffectRepository = StubVmEffectRepo,
    ),
)

private object StubVmEssenceRepo : EssenceRepository {
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
        combination: wizardry.compendium.domain.model.ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence) = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private object StubVmStoneRepo : AwakeningStoneRepository {
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

private object StubVmListingRepo : AbilityListingRepository {
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

private object StubVmEffectRepo : StatusEffectRepository {
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
