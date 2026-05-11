package wizardry.compendium.characterbuild.contributions

import androidx.lifecycle.SavedStateHandle
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
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterBuildContributionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `Create mode initial form is blank`() = runTest {
        val vm = create(savedName = null)
        advanceUntilIdle()

        val mode = vm.mode.first { it is CharacterBuildContributionsViewModel.Mode.Create }
        assertEquals(CharacterBuildContributionsViewModel.Mode.Create, mode)
        assertEquals("", vm.formState.value.name)
        assertEquals("", vm.formState.value.race)
    }

    @Test
    fun `Edit mode loads existing build into form`() = runTest {
        val build = build("Jason", "Outworlder")
        val vm = create(savedName = "Jason", existingBuilds = listOf(build))
        advanceUntilIdle()

        val mode = vm.mode.first { it is CharacterBuildContributionsViewModel.Mode.Edit.Ready }
        assertTrue(mode is CharacterBuildContributionsViewModel.Mode.Edit.Ready)
        assertEquals("Jason", vm.formState.value.name)
        assertEquals("Outworlder", vm.formState.value.race)
    }

    @Test
    fun `Edit mode emits NotFound when name doesn't match`() = runTest {
        val vm = create(savedName = "ghost", existingBuilds = emptyList())
        advanceUntilIdle()

        val mode = vm.mode.first { it is CharacterBuildContributionsViewModel.Mode.Edit.NotFound }
        assertTrue(mode is CharacterBuildContributionsViewModel.Mode.Edit.NotFound)
    }

    @Test
    fun `Save in Create mode succeeds when name is unique`() = runTest {
        val repo = FakeBuildRepo(emptyList())
        val vm = create(savedName = null, repo = repo)
        advanceUntilIdle()

        vm.setName("Jason")
        vm.setRace("Outworlder")
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is CharacterBuildContributionsViewModel.SaveState.Success }
        assertTrue(state is CharacterBuildContributionsViewModel.SaveState.Success)
        assertEquals(listOf("Jason"), repo.allNames())
    }

    @Test
    fun `Save in Create mode rejects duplicate name with structured error`() = runTest {
        val repo = FakeBuildRepo(listOf(build("Jason", "Outworlder")))
        val vm = create(savedName = null, repo = repo)
        advanceUntilIdle()

        vm.setName("Jason")
        vm.setRace("Other")
        vm.save()
        advanceUntilIdle()

        val state = vm.saveState.first { it is CharacterBuildContributionsViewModel.SaveState.Error }
            as CharacterBuildContributionsViewModel.SaveState.Error
        assertTrue(state.message.contains("already exists"))
        assertEquals(1, repo.allNames().size)
    }

    @Test
    fun `changing essence on populated slot raises EssenceChangePrompt`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val reaper = listing("Hand of the Reaper")
        val build = build("Jason", "Outworlder").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(sin, listOf(Ability.Listing.of("Hand of the Reaper").acquire(sin)))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "Jason",
            existingBuilds = listOf(build),
            essences = listOf(sin, doom),
            listings = listOf(reaper),
        )
        advanceUntilIdle()

        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, doom)
        advanceUntilIdle()

        val prompt = vm.essenceChangePrompt.first { it != null }
        assertEquals(CharacterBuildContributionsViewModel.Slot.Power, prompt!!.slot)
        assertEquals("Doom", prompt.target?.name)
    }

    @Test
    fun `confirmEssenceChangeClearingAbilities applies new essence and empties ability list`() = runTest {
        val (_, doom, vm) = setupForEssenceChange()
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, doom)
        advanceUntilIdle()

        vm.confirmEssenceChangeClearingAbilities()
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Doom", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals(emptyList<Ability.Acquired>(), form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.abilities)
    }

    @Test
    fun `confirmEssenceChangeKeepingAbilities applies new essence and retains ability list`() = runTest {
        val (_, doom, vm) = setupForEssenceChange()
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, doom)
        advanceUntilIdle()

        vm.confirmEssenceChangeKeepingAbilities()
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Doom", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals(
            listOf("Hand of the Reaper"),
            form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.abilities?.map { it.name },
        )
    }

    @Test
    fun `cancelEssenceChange leaves the slot untouched`() = runTest {
        val (_, doom, vm) = setupForEssenceChange()
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, doom)
        advanceUntilIdle()

        vm.cancelEssenceChange()
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Sin", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals(
            listOf("Hand of the Reaper"),
            form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.abilities?.map { it.name },
        )
    }

    @Test
    fun `addAbilityToSlot enforces 5-cap`() = runTest {
        val sin = manifestation("Sin")
        val listings = (1..6).map { listing("a$it") }
        val vm = create(savedName = null, essences = listOf(sin), listings = listings)
        advanceUntilIdle()
        vm.setName("X"); vm.setRace("Y")
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, sin)
        advanceUntilIdle()

        listings.take(5).forEach { vm.addAbilityToSlot(CharacterBuildContributionsViewModel.Slot.Power, it) }
        advanceUntilIdle()

        // attempting a sixth is a no-op
        vm.addAbilityToSlot(CharacterBuildContributionsViewModel.Slot.Power, listings[5])
        advanceUntilIdle()

        assertEquals(5, vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.abilities?.size)
    }

    @Test
    fun `confluence essence assignment is preserved through save and reload`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val magic = manifestation("Magic")
        val balance = confluence("Balance", setOf(sin, doom, magic))
        val repo = FakeBuildRepo(emptyList())
        val vm = create(
            savedName = null,
            essences = listOf(sin, doom, magic, balance),
            repo = repo,
        )
        advanceUntilIdle()

        vm.setName("Confluencer")
        vm.setRace("Outworlder")
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, balance)
        advanceUntilIdle()

        val slotEssence = vm.formState.value
            .attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence
        assertTrue("expected confluence in slot, got $slotEssence", slotEssence is Essence.Confluence)
        assertEquals("Balance", slotEssence?.name)

        vm.save()
        advanceUntilIdle()

        val saved = repo.allBuilds().single()
        val savedEssence = saved.Power.essence?.essence
        assertTrue("expected saved confluence, got $savedEssence", savedEssence is Essence.Confluence)
        assertEquals("Balance", savedEssence?.name)
    }

    @Test
    fun `confluence is selectable even when other slots are empty`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val magic = manifestation("Magic")
        val balance = confluence("Balance", setOf(sin, doom, magic))
        val vm = create(
            savedName = null,
            essences = listOf(sin, doom, magic, balance),
        )
        advanceUntilIdle()

        vm.setName("X"); vm.setRace("Y")
        // No other slots populated; this used to be blocked by isFinalEssencePick.
        vm.requestEssenceChange(CharacterBuildContributionsViewModel.Slot.Power, balance)
        advanceUntilIdle()

        val slot = vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Power]
        assertEquals("Balance", slot?.essence?.name)
    }

    @Test
    fun `addRacialAbility enforces 6-cap`() = runTest {
        val racials = (1..7).map { racialListing("r$it") }
        val vm = create(savedName = null, listings = racials)
        advanceUntilIdle()

        racials.take(6).forEach { vm.addRacialAbility(it) }
        vm.addRacialAbility(racials[6])
        advanceUntilIdle()

        assertEquals(6, vm.formState.value.racialAbilities.size)
    }

    // --- helpers -------------------------------------------------------

    private data class SetupForEssenceChange(
        val sin: Essence.Manifestation,
        val doom: Essence.Manifestation,
        val vm: CharacterBuildContributionsViewModel,
    )

    private fun setupForEssenceChange(): SetupForEssenceChange {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val reaper = listing("Hand of the Reaper")
        val build = build("Jason", "Outworlder").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(sin, listOf(Ability.Listing.of("Hand of the Reaper").acquire(sin)))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "Jason",
            existingBuilds = listOf(build),
            essences = listOf(sin, doom),
            listings = listOf(reaper),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return SetupForEssenceChange(sin, doom, vm)
    }

    private fun create(
        savedName: String?,
        existingBuilds: List<CharacterBuild> = emptyList(),
        essences: List<Essence> = emptyList(),
        listings: List<Ability.Listing> = emptyList(),
        repo: FakeBuildRepo = FakeBuildRepo(existingBuilds),
    ): CharacterBuildContributionsViewModel {
        val savedState = SavedStateHandle()
        if (savedName != null) savedState["name"] = savedName
        return CharacterBuildContributionsViewModel(
            savedStateHandle = savedState,
            buildRepository = repo,
            essenceRepository = FakeEssenceRepo(essences),
            abilityListingRepository = FakeAbilityListingRepo(listings),
        )
    }

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name, rank = Rank.Unranked, rarity = Rarity.Unknown,
        properties = emptyList(), description = "", isRestricted = false,
    )

    private fun confluence(name: String, members: Set<Essence.Manifestation>) =
        Essence.Confluence(
            name = name,
            confluenceSets = setOf(ConfluenceSet(set = members)),
            isRestricted = false,
        )

    private fun listing(name: String): Ability.Listing = Ability.Listing(
        name = name,
        effects = listOf(
            Effect.AbilityEffect(
                rank = Rank.Iron, type = AbilityType.Spell, properties = emptyList(),
                cost = emptyList(), cooldown = Duration.ZERO, description = "",
                replacementKey = null,
            ),
        ),
    )

    private fun racialListing(name: String): Ability.Listing = Ability.Listing(
        name = name,
        effects = listOf(
            Effect.AbilityEffect(
                rank = Rank.Iron, type = AbilityType.RacialAbility, properties = emptyList(),
                cost = emptyList(), cooldown = Duration.ZERO, description = "",
                replacementKey = null,
            ),
        ),
    )

    private fun build(name: String, race: String) =
        CharacterBuild(name = name, race = race, racialAbilities = emptyList())

    private class FakeBuildRepo(initial: List<CharacterBuild>) : CharacterBuildRepository {
        private val flow = MutableStateFlow(initial)
        override val builds: Flow<List<CharacterBuild>> = flow
        override suspend fun getBuilds() = flow.value
        override suspend fun getBuild(name: String) = flow.value.firstOrNull { it.name == name }
        override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult {
            flow.value = flow.value.filterNot { it.name == build.name } + build
            return ContributionResult.Success
        }
        override suspend fun deleteContribution(name: String): ContributionResult {
            if (flow.value.none { it.name == name }) return ContributionResult.Failure("nope")
            flow.value = flow.value.filterNot { it.name == name }
            return ContributionResult.Success
        }
        fun allNames(): List<String> = flow.value.map { it.name }
        fun allBuilds(): List<CharacterBuild> = flow.value
    }

    private class FakeEssenceRepo(private val data: List<Essence>) : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(data)
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences() = data
        override suspend fun getContributions() = emptyList<Essence>()
        override suspend fun getConflicts() = emptyList<EssenceConflict>()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>) = ContributionResult.Success
        override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun updateConfluenceContribution(confluence: Essence.Confluence) = ContributionResult.Success
    }

    private class FakeAbilityListingRepo(private val data: List<Ability.Listing>) : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(data)
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings() = data
        override suspend fun getContributions() = emptyList<Ability.Listing>()
        override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    }
}
