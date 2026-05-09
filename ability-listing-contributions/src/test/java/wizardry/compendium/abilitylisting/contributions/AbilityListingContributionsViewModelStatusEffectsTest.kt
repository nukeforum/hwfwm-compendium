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
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

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
        ).also { it.ioDispatcher = dispatcher }
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
        override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            ContributionResult.Failure("not used")
    }
}
