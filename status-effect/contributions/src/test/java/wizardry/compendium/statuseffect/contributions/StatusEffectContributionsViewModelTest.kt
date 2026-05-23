package wizardry.compendium.statuseffect.contributions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.share.StatusEffectShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

class StatusEffectContributionsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun teardown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun effect(name: String) = StatusEffect(
        name = name, type = StatusType.Affliction.Curse,
        properties = emptyList(), stackable = false, description = "",
    )

    private class FakeRepo(
        private val items: List<StatusEffect> = emptyList(),
        private val contributions: Set<String> = emptySet(),
        var saveResult: ContributionResult = ContributionResult.Success,
    ) : StatusEffectRepository {
        var lastSaved: StatusEffect? = null
        var lastUpdated: StatusEffect? = null
        var lastDeleted: String? = null
        override val statusEffects = flowOf(items)
        override val conflicts = flowOf(emptyList<StatusEffectConflict>())
        override suspend fun getStatusEffects() = items
        override suspend fun getContributions() = items.filter { it.name in contributions }
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult {
            lastSaved = effect; return saveResult
        }
        override suspend fun isContribution(name: String) = name in contributions
        override suspend fun deleteContribution(name: String): ContributionResult {
            lastDeleted = name; return saveResult
        }
        override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect): ContributionResult {
            lastUpdated = effect; return saveResult
        }
        override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }

    @Test
    fun `create mode saves a new effect`() = runTest {
        val repo = FakeRepo()
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(), repo, stubShareUseCase())
        vm.save("Burn", StatusType.Affliction.Elemental, listOf(Property.Fire), stackable = true, description = "burn")
        advanceUntilIdle()
        assertEquals(StatusEffect(
            name = "Burn", type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire), stackable = true, description = "burn",
        ), repo.lastSaved)
        assertEquals(StatusEffectContributionsViewModel.SaveState.Success, vm.saveState.value)
    }

    @Test
    fun `edit mode loads existing then updates`() = runTest {
        val burn = effect("Burn")
        val repo = FakeRepo(items = listOf(burn), contributions = setOf("Burn"))
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(mapOf("name" to "Burn")), repo, stubShareUseCase())
        advanceUntilIdle()
        assertTrue(vm.mode.value is StatusEffectContributionsViewModel.Mode.Edit.Ready)
        vm.save("Burn", StatusType.Affliction.Elemental, emptyList(), stackable = false, description = "")
        advanceUntilIdle()
        assertEquals("Burn", repo.lastUpdated?.name)
    }

    @Test
    fun `blank name yields error state`() = runTest {
        val repo = FakeRepo()
        val vm = StatusEffectContributionsViewModel(SavedStateHandle(), repo, stubShareUseCase())
        vm.save("", StatusType.Affliction.Curse, emptyList(), stackable = false, description = "")
        advanceUntilIdle()
        val s = vm.saveState.value
        assertTrue(s is StatusEffectContributionsViewModel.SaveState.Error)
    }
}

private fun stubShareUseCase(): StatusEffectShareUseCase = StatusEffectShareUseCase(
    wireIo = WireIoRepository(
        essenceRepository = VmStubEssenceRepo,
        awakeningStoneRepository = VmStubStoneRepo,
        abilityListingRepository = VmStubListingRepo,
        statusEffectRepository = VmStubEffectRepo,
    ),
)

private object VmStubEssenceRepo : EssenceRepository {
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

private object VmStubStoneRepo : AwakeningStoneRepository {
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

private object VmStubListingRepo : AbilityListingRepository {
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

private object VmStubEffectRepo : StatusEffectRepository {
    override val statusEffects = flowOf(emptyList<StatusEffect>())
    override val conflicts = flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
}
