package wizardry.compendium.conflicts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state aggregates conflicts across all three domains`() = runTest {
        val essenceRepo = FakeEssenceRepo(
            initialConflicts = listOf(
                EssenceConflict.NameCollision(
                    contribution = manifestation("Wind"),
                    canonical = manifestation("Wind"),
                ),
            ),
        )
        val stoneRepo = FakeStoneRepo(
            initialConflicts = listOf(
                AwakeningStoneConflict.NameCollision(stone("Granite"), stone("Granite")),
            ),
        )
        val abilityRepo = FakeAbilityRepo(
            initialConflicts = listOf(
                AbilityListingConflict.NameCollision(listing("Fireball"), listing("Fireball")),
            ),
        )
        val vm = ConflictsViewModel(essenceRepo, stoneRepo, abilityRepo, FakeStatusEffectRepo())

        // Subscribe so stateIn starts collecting
        val collector = launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.essence.size)
        assertEquals(1, state.awakeningStone.size)
        assertEquals(1, state.abilityListing.size)
        assertEquals(3, state.total)

        collector.cancel()
    }

    @Test
    fun `delete essence contribution dispatches to repository`() = runTest {
        val essenceRepo = FakeEssenceRepo()
        val vm = ConflictsViewModel(essenceRepo, FakeStoneRepo(), FakeAbilityRepo(), FakeStatusEffectRepo())

        vm.deleteEssenceContribution("Wind")
        advanceUntilIdle()

        assertEquals(listOf("Wind"), essenceRepo.deleted)
    }

    @Test
    fun `remove single combination keeps remaining combinations on contribution`() = runTest {
        val essenceRepo = FakeEssenceRepo()
        val vm = ConflictsViewModel(essenceRepo, FakeStoneRepo(), FakeAbilityRepo(), FakeStatusEffectRepo())

        val original = confluence(
            "Doom",
            setOf(set("A", "B", "C"), set("D", "E", "F")),
        )
        vm.removeCombinationFromContribution(original, set("A", "B", "C"))
        advanceUntilIdle()

        assertEquals(1, essenceRepo.confluenceUpdates.size)
        assertEquals(setOf(set("D", "E", "F")), essenceRepo.confluenceUpdates.first().confluenceSets)
        assertEquals(emptyList<String>(), essenceRepo.deleted)
    }

    @Test
    fun `remove last combination deletes the contribution entirely`() = runTest {
        val essenceRepo = FakeEssenceRepo()
        val vm = ConflictsViewModel(essenceRepo, FakeStoneRepo(), FakeAbilityRepo(), FakeStatusEffectRepo())

        val original = confluence("Doom", setOf(set("A", "B", "C")))
        vm.removeCombinationFromContribution(original, set("A", "B", "C"))
        advanceUntilIdle()

        assertEquals(emptyList<Essence.Confluence>(), essenceRepo.confluenceUpdates)
        assertEquals(listOf("Doom"), essenceRepo.deleted)
    }
}

private class FakeEssenceRepo(
    private val initialConflicts: List<EssenceConflict> = emptyList(),
) : EssenceRepository {
    val deleted = mutableListOf<String>()
    val confluenceUpdates = mutableListOf<Essence.Confluence>()

    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(initialConflicts)
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = initialConflicts
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
    override suspend fun deleteContribution(name: String): ContributionResult {
        deleted += name
        return ContributionResult.Success
    }
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun updateConfluenceContribution(
        confluence: Essence.Confluence,
    ): ContributionResult {
        confluenceUpdates += confluence
        return ContributionResult.Success
    }
}

private class FakeStoneRepo(
    private val initialConflicts: List<AwakeningStoneConflict> = emptyList(),
) : AwakeningStoneRepository {
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(initialConflicts)
    override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
    override suspend fun getContributions(): List<AwakeningStone> = emptyList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = initialConflicts
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
}

private class FakeAbilityRepo(
    private val initialConflicts: List<AbilityListingConflict> = emptyList(),
) : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(initialConflicts)
    override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = initialConflicts
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
}

private class FakeStatusEffectRepo(
    private val initialConflicts: List<StatusEffectConflict> = emptyList(),
) : StatusEffectRepository {
    override val statusEffects: Flow<List<StatusEffect>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(initialConflicts)
    override suspend fun getStatusEffects(): List<StatusEffect> = emptyList()
    override suspend fun getContributions(): List<StatusEffect> = emptyList()
    override suspend fun getConflicts(): List<StatusEffectConflict> = initialConflicts
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun confluence(name: String, combinations: Set<ConfluenceSet>): Essence.Confluence =
    Essence.Confluence(name = name, confluenceSets = combinations, isRestricted = false)

private fun set(a: String, b: String, c: String): ConfluenceSet =
    ConfluenceSet(manifestation(a), manifestation(b), manifestation(c))

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)

private fun listing(name: String): Ability.Listing =
    Ability.Listing(name = name, effects = emptyList())
