package wizardry.compendium.abilitylisting.contributions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.flowOf
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
import wizardry.compendium.share.AbilityListingShareUseCase
import wizardry.compendium.wire.repo.WireIoRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AbilityListingContributionsViewModelStatusEffectsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `statusEffects flow emits the repository's current effects`() = runTest {
        val repo = FakeStatusEffectRepository(
            initial = listOf(
                StatusEffect(
                    name = "Bleeding",
                    type = StatusType.Affliction.Wound,
                    properties = listOf(Property.Blood),
                    stackable = true,
                    description = "x",
                ),
            ),
        )
        val vm = AbilityListingContributionsViewModel(
            savedStateHandle = SavedStateHandle(),
            abilityListingRepository = NoopAbilityListingRepository,
            statusEffectRepository = repo,
            ioDispatcher = dispatcher,
            shareUseCase = stubShareUseCase(),
        )
        assertEquals(listOf("Bleeding"), vm.statusEffects.first().map { it.name })
    }

    private class FakeStatusEffectRepository(
        initial: List<StatusEffect>,
    ) : StatusEffectRepository {
        private val flow = MutableStateFlow(initial)
        override val statusEffects: Flow<List<StatusEffect>> = flow
        override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects(): List<StatusEffect> = flow.value
        override suspend fun getContributions(): List<StatusEffect> = emptyList()
        override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult =
            ContributionResult.Failure("not used")
    }

    private object NoopAbilityListingRepository : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun isContribution(name: String): Boolean = false
        override suspend fun deleteContribution(name: String): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
            ContributionResult.Failure("not used")
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }
}

private fun stubShareUseCase(): AbilityListingShareUseCase = AbilityListingShareUseCase(
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
    override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
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
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
}
