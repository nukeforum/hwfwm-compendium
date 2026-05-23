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
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.share.CharacterBuildShareUseCase
import wizardry.compendium.share.DecodedSingle
import wizardry.compendium.wire.repo.WireIoRepository
import wizardry.compendium.wire.share.BuildShareDecoder
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

    @Test
    fun `confluencePickerRowsFor sorts by match count desc, ties alphabetical`() = runTest {
        val a = manifestation("Alpha")
        val b = manifestation("Bravo")
        val c = manifestation("Charlie")
        val noMatch = manifestation("Delta")
        // Three confluences; "Balance" gets 2 matches, "Catalyst" and "Aurora" get 1 each.
        val balance = confluence("Balance", setOf(a, b, noMatch))
        val aurora = confluence("Aurora", setOf(a, c, noMatch))
        val catalyst = confluence("Catalyst", setOf(a, noMatch, c))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, noMatch, balance, aurora, catalyst),
        )
        advanceUntilIdle()

        val rows = vm.confluencePickerRowsFor(CharacterBuildContributionsViewModel.Slot.Recovery)

        assertEquals(listOf("Balance", "Aurora", "Catalyst"), rows.map { it.confluence.name })
        assertEquals(2, rows[0].matchedEssences.size)
        assertEquals(1, rows[1].matchedEssences.size)
        assertEquals(1, rows[2].matchedEssences.size)
    }

    @Test
    fun `confluencePickerRowsFor subtitle is union of matched user essences`() = runTest {
        val magic = manifestation("Magic")
        val sand = manifestation("Sand")
        val fire = manifestation("Fire")
        val sun = manifestation("Sun")
        val earth = manifestation("Earth")
        // Sets: {Magic, Fire, Sand} and {Magic, Sun, Earth} — user has Magic + Sand.
        val heat = Essence.Confluence(
            name = "Heat",
            confluenceSets = setOf(
                ConfluenceSet(setOf(magic, fire, sand)),
                ConfluenceSet(setOf(magic, sun, earth)),
            ),
            isRestricted = false,
        )
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(magic)),
                Attribute.Speed(essence = AbsorbedEssence(sand)),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(magic, sand, fire, sun, earth, heat),
        )
        advanceUntilIdle()

        val rows = vm.confluencePickerRowsFor(CharacterBuildContributionsViewModel.Slot.Recovery)
        val heatRow = rows.single { it.confluence.name == "Heat" }
        // Magic is in both sets, Sand in one — union = {Magic, Sand}.
        assertEquals(setOf("Magic", "Sand"), heatRow.matchedEssences.map { it.name }.toSet())
    }

    @Test
    fun `confluencePickerRowsFor returns empty matchedEssences when nothing aligns`() = runTest {
        val sin = manifestation("Sin")
        val unrelated1 = manifestation("Unrelated1")
        val unrelated2 = manifestation("Unrelated2")
        val unrelated3 = manifestation("Unrelated3")
        val balance = confluence("Balance", setOf(unrelated1, unrelated2, unrelated3))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(sin)),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(sin, unrelated1, unrelated2, unrelated3, balance),
        )
        advanceUntilIdle()

        val rows = vm.confluencePickerRowsFor(CharacterBuildContributionsViewModel.Slot.Recovery)
        assertEquals(emptyList<Essence.Manifestation>(), rows.single().matchedEssences)
    }

    @Test
    fun `compatibleSetsFor filters sets whose membership covers existing other-slot essences`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val confl = Essence.Confluence(
            name = "Combo",
            confluenceSets = setOf(
                ConfluenceSet(setOf(a, b, c)),  // covers user (A, B)
                ConfluenceSet(setOf(a, c, d)),  // does NOT cover user (B missing)
            ),
            isRestricted = false,
        )
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, d, confl),
        )
        advanceUntilIdle()

        val sets = vm.compatibleSetsFor(CharacterBuildContributionsViewModel.Slot.Recovery, confl)
        assertEquals(1, sets.size)
        assertEquals(setOf("A", "B", "C"), sets.single().set.map { it.name }.toSet())
    }

    @Test
    fun `applyConfluence with set fills empty slots in Slot order with alphabetical leftovers`() = runTest {
        val a = manifestation("Aardvark")
        val b = manifestation("Buffalo")
        val c = manifestation("Cougar")
        val combo = confluence("Combo", setOf(a, b, c))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, combo),
        )
        advanceUntilIdle()

        val theSet = ConfluenceSet(setOf(a, b, c))
        vm.applyConfluence(CharacterBuildContributionsViewModel.Slot.Recovery, combo, theSet)
        advanceUntilIdle()

        val form = vm.formState.value
        // Power keeps Aardvark; Recovery gets Combo; empty Speed gets Buffalo (B < C); Spirit gets Cougar.
        assertEquals("Aardvark", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals("Buffalo", form.attributes[CharacterBuildContributionsViewModel.Slot.Speed]?.essence?.name)
        assertEquals("Cougar", form.attributes[CharacterBuildContributionsViewModel.Slot.Spirit]?.essence?.name)
        assertEquals("Combo", form.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
    }

    @Test
    fun `applyConfluence with set preserves existing matching essence and its abilities`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val magic = manifestation("Magic")
        val combo = confluence("Combo", setOf(sin, doom, magic))
        val reaper = listing("Hand of the Reaper")
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(sin, listOf(Ability.Listing.of("Hand of the Reaper").acquire(sin)))),
                Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(sin, doom, magic, combo),
            listings = listOf(reaper),
        )
        advanceUntilIdle()

        vm.applyConfluence(
            CharacterBuildContributionsViewModel.Slot.Recovery,
            combo,
            ConfluenceSet(setOf(sin, doom, magic)),
        )
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Sin", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals(
            listOf("Hand of the Reaper"),
            form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.abilities?.map { it.name },
        )
    }

    @Test
    fun `applyConfluence with null set assigns Confluence to target slot only`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val unrelated1 = manifestation("Far1")
        val unrelated2 = manifestation("Far2")
        val unrelated3 = manifestation("Far3")
        // Confluence with a set that doesn't include sin or doom.
        val combo = confluence("Combo", setOf(unrelated1, unrelated2, unrelated3))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(sin)),
                Attribute.Speed(essence = AbsorbedEssence(doom)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(sin, doom, unrelated1, unrelated2, unrelated3, combo),
        )
        advanceUntilIdle()

        vm.applyConfluence(CharacterBuildContributionsViewModel.Slot.Recovery, combo, null)
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Sin", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals("Doom", form.attributes[CharacterBuildContributionsViewModel.Slot.Speed]?.essence?.name)
        assertEquals(null, form.attributes[CharacterBuildContributionsViewModel.Slot.Spirit]?.essence)
        assertEquals("Combo", form.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
    }

    @Test
    fun `applyConfluence raises essenceChangePrompt for target slot when target has abilities`() = runTest {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val magic = manifestation("Magic")
        val combo = confluence("Combo", setOf(sin, doom, magic))
        val reaper = listing("Hand of the Reaper")
        val build = build("X", "Y").copy(
            attributes = setOf(
                // Recovery (the target slot) has abilities.
                Attribute.Recovery(essence = AbsorbedEssence(sin, listOf(Ability.Listing.of("Hand of the Reaper").acquire(sin)))),
                Attribute.Power(), Attribute.Speed(), Attribute.Spirit(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(sin, doom, magic, combo),
            listings = listOf(reaper),
        )
        advanceUntilIdle()

        vm.applyConfluence(
            CharacterBuildContributionsViewModel.Slot.Recovery,
            combo,
            ConfluenceSet(setOf(sin, doom, magic)),
        )
        advanceUntilIdle()

        val prompt = vm.essenceChangePrompt.first { it != null }
        assertEquals(CharacterBuildContributionsViewModel.Slot.Recovery, prompt!!.slot)
        assertEquals("Combo", prompt.target?.name)
    }

    @Test
    fun `requestConfluencePick with 1 compatible set applies it immediately`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val combo = confluence("Combo", setOf(a, b, c))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Combo", form.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
        assertEquals("C", form.attributes[CharacterBuildContributionsViewModel.Slot.Spirit]?.essence?.name)
        assertEquals(null, vm.confluenceSetPrompt.value)
        assertEquals(null, vm.saveCombinationPrompt.value)
    }

    @Test
    fun `requestConfluencePick with 2 plus compatible sets raises confluenceSetPrompt`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val combo = Essence.Confluence(
            name = "Combo",
            confluenceSets = setOf(
                ConfluenceSet(setOf(a, b, c)),
                ConfluenceSet(setOf(a, b, d)),
            ),
            isRestricted = false,
        )
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, d, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        val prompt = vm.confluenceSetPrompt.value
        assertEquals(CharacterBuildContributionsViewModel.Slot.Recovery, prompt?.slot)
        assertEquals("Combo", prompt?.confluence?.name)
        assertEquals(2, prompt?.sets?.size)
        // The Confluence is NOT yet assigned to the slot — we wait for the user to pick a set.
        assertEquals(null, vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence)
    }

    @Test
    fun `requestConfluencePick with 0 compatible and 3 other essences raises saveCombinationPrompt`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z)) // user's a/b/c don't appear
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        val prompt = vm.saveCombinationPrompt.value
        assertEquals(CharacterBuildContributionsViewModel.Slot.Recovery, prompt?.slot)
        assertEquals("Combo", prompt?.confluence?.name)
        assertEquals(setOf("A", "B", "C"), prompt?.combination?.map { it.name }?.toSet())
        // No slot change yet.
        assertEquals(null, vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence)
    }

    @Test
    fun `requestConfluencePick with 0 compatible and 1-2 other essences assigns Confluence directly`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, x, y, z, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Combo", form.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
        assertEquals("A", form.attributes[CharacterBuildContributionsViewModel.Slot.Power]?.essence?.name)
        assertEquals("B", form.attributes[CharacterBuildContributionsViewModel.Slot.Speed]?.essence?.name)
        assertEquals(null, vm.confluenceSetPrompt.value)
        assertEquals(null, vm.saveCombinationPrompt.value)
    }

    @Test
    fun `confirmConfluenceSetPick applies the chosen set and clears prompt`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val combo = Essence.Confluence(
            name = "Combo",
            confluenceSets = setOf(
                ConfluenceSet(setOf(a, b, c)),
                ConfluenceSet(setOf(a, b, d)),
            ),
            isRestricted = false,
        )
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, d, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        val secondSet = ConfluenceSet(setOf(a, b, d))
        vm.confirmConfluenceSetPick(secondSet)
        advanceUntilIdle()

        val form = vm.formState.value
        assertEquals("Combo", form.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
        assertEquals("D", form.attributes[CharacterBuildContributionsViewModel.Slot.Spirit]?.essence?.name)
        assertEquals(null, vm.confluenceSetPrompt.value)
    }

    @Test
    fun `cancelConfluenceSetPick clears the prompt without changing slots`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val combo = Essence.Confluence(
            name = "Combo",
            confluenceSets = setOf(
                ConfluenceSet(setOf(a, b, c)),
                ConfluenceSet(setOf(a, b, d)),
            ),
            isRestricted = false,
        )
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), Attribute.Recovery(),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, d, combo),
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        vm.cancelConfluenceSetPick()
        advanceUntilIdle()

        assertEquals(null, vm.confluenceSetPrompt.value)
        assertEquals(null, vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence)
    }

    @Test
    fun `confirmSaveCombination writes the new ConfluenceSet and completes the pick`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(),
            ),
        )
        val essenceRepo = RecordingEssenceRepo(listOf(a, b, c, x, y, z, combo))
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
            essenceRepo = essenceRepo,
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        vm.confirmSaveCombination()
        advanceUntilIdle()

        val recorded = essenceRepo.combinationsAdded.single()
        assertEquals("Combo", recorded.first.name)
        assertEquals(setOf("A", "B", "C"), recorded.second.set.map { it.name }.toSet())
        assertEquals("Combo", vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
        assertEquals(null, vm.saveCombinationPrompt.value)
    }

    @Test
    fun `dismissSaveCombinationPrompt complete=true assigns Confluence without writing`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(),
            ),
        )
        val essenceRepo = RecordingEssenceRepo(listOf(a, b, c, x, y, z, combo))
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
            essenceRepo = essenceRepo,
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        vm.dismissSaveCombinationPrompt(complete = true)
        advanceUntilIdle()

        assertTrue(essenceRepo.combinationsAdded.isEmpty())
        assertEquals("Combo", vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence?.name)
        assertEquals(null, vm.saveCombinationPrompt.value)
    }

    @Test
    fun `dismissSaveCombinationPrompt complete=false leaves everything unchanged`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(),
            ),
        )
        val essenceRepo = RecordingEssenceRepo(listOf(a, b, c, x, y, z, combo))
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
            essenceRepo = essenceRepo,
        )
        advanceUntilIdle()

        vm.requestConfluencePick(CharacterBuildContributionsViewModel.Slot.Recovery, combo)
        advanceUntilIdle()

        vm.dismissSaveCombinationPrompt(complete = false)
        advanceUntilIdle()

        assertTrue(essenceRepo.combinationsAdded.isEmpty())
        assertEquals(null, vm.formState.value.attributes[CharacterBuildContributionsViewModel.Slot.Recovery]?.essence)
        assertEquals(null, vm.saveCombinationPrompt.value)
    }

    @Test
    fun `confluenceWarning is set when 3 manifestations do not match any Confluence set`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(essence = AbsorbedEssence(combo)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
        )
        advanceUntilIdle()

        val warning = vm.confluenceWarning.first { it != null }
        assertEquals(CharacterBuildContributionsViewModel.Slot.Recovery, warning)
    }

    @Test
    fun `confluenceWarning is null when 3 manifestations match a Confluence set`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val combo = confluence("Combo", setOf(a, b, c))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(essence = AbsorbedEssence(combo)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, combo),
        )
        advanceUntilIdle()

        assertEquals(null, vm.confluenceWarning.value)
    }

    @Test
    fun `confluenceWarning is null when any slot is empty`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(), // empty
                Attribute.Recovery(essence = AbsorbedEssence(combo)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, x, y, z, combo),
        )
        advanceUntilIdle()

        assertEquals(null, vm.confluenceWarning.value)
    }

    @Test
    fun `confluenceWarning is null when zero Confluences are picked`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(essence = AbsorbedEssence(d)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, d),
        )
        advanceUntilIdle()

        assertEquals(null, vm.confluenceWarning.value)
    }

    @Test
    fun `confluenceWarning is null when 2+ Confluences are picked`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo1 = confluence("Combo1", setOf(x, y, z))
        val combo2 = confluence("Combo2", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(combo1)),
                Attribute.Recovery(essence = AbsorbedEssence(combo2)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, x, y, z, combo1, combo2),
        )
        advanceUntilIdle()

        assertEquals(null, vm.confluenceWarning.value)
    }

    @Test
    fun `resolveWarningSaveCombination raises saveCombinationPrompt for the warning slot`() = runTest {
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val x = manifestation("X1")
        val y = manifestation("Y1")
        val z = manifestation("Z1")
        val combo = confluence("Combo", setOf(x, y, z))
        val build = build("X", "Y").copy(
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(a)),
                Attribute.Speed(essence = AbsorbedEssence(b)),
                Attribute.Spirit(essence = AbsorbedEssence(c)),
                Attribute.Recovery(essence = AbsorbedEssence(combo)),
            ),
        )
        val vm = create(
            savedName = "X",
            existingBuilds = listOf(build),
            essences = listOf(a, b, c, x, y, z, combo),
        )
        advanceUntilIdle()

        vm.resolveWarningSaveCombination()
        advanceUntilIdle()

        val prompt = vm.saveCombinationPrompt.value
        assertEquals(CharacterBuildContributionsViewModel.Slot.Recovery, prompt?.slot)
        assertEquals("Combo", prompt?.confluence?.name)
        assertEquals(setOf("A", "B", "C"), prompt?.combination?.map { it.name }?.toSet())
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
        essenceRepo: EssenceRepository = FakeEssenceRepo(essences),
    ): CharacterBuildContributionsViewModel {
        val savedState = SavedStateHandle()
        if (savedName != null) savedState["name"] = savedName
        return CharacterBuildContributionsViewModel(
            savedStateHandle = savedState,
            buildRepository = repo,
            essenceRepository = essenceRepo,
            abilityListingRepository = FakeAbilityListingRepo(listings),
            shareUseCase = NoOpBuildShareUseCase(),
        )
    }

    private class NoOpBuildShareUseCase : CharacterBuildShareUseCase(
        wireIo = WireIoRepository(
            essenceRepository = NoOpEssenceRepoForDecoder(),
            awakeningStoneRepository = NoOpStoneRepoForDecoder(),
            abilityListingRepository = NoOpListingRepoForDecoder(),
            statusEffectRepository = NoOpStatusEffectRepoForDecoder(),
        ),
        buildShareDecoder = BuildShareDecoder(
            essenceRepository = NoOpEssenceRepoForDecoder(),
            abilityListingRepository = NoOpListingRepoForDecoder(),
            buildRepository = NoOpBuildRepoForDecoder(),
        ),
    ) {
        override suspend fun decodeBuildBundle(text: String): DecodedSingle<wizardry.compendium.wire.share.BuildImportPreview> =
            DecodedSingle.Failed("import not exercised in this test")
    }

    private class NoOpStoneRepoForDecoder : wizardry.compendium.repositories.AwakeningStoneRepository {
        override val awakeningStones: Flow<List<wizardry.compendium.domain.model.AwakeningStone>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<wizardry.compendium.repositories.AwakeningStoneConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAwakeningStones() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
        override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.AwakeningStone>()
        override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.AwakeningStoneConflict>()
        override suspend fun saveAwakeningStoneContribution(stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateAwakeningStoneContribution(stone: wizardry.compendium.domain.model.AwakeningStone) = ContributionResult.Success
    }

    private class NoOpStatusEffectRepoForDecoder : wizardry.compendium.repositories.StatusEffectRepository {
        override val statusEffects: Flow<List<wizardry.compendium.domain.model.StatusEffect>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<wizardry.compendium.repositories.StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects() = emptyList<wizardry.compendium.domain.model.StatusEffect>()
        override suspend fun getContributions() = emptyList<wizardry.compendium.domain.model.StatusEffect>()
        override suspend fun getConflicts() = emptyList<wizardry.compendium.repositories.StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: wizardry.compendium.domain.model.StatusEffect) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateStatusEffectContribution(effect: wizardry.compendium.domain.model.StatusEffect) = ContributionResult.Success
    }

    private class NoOpEssenceRepoForDecoder : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences(): List<Essence> = emptyList()
        override suspend fun getContributions(): List<Essence> = emptyList()
        override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>) = ContributionResult.Success
        override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun updateConfluenceContribution(confluence: Essence.Confluence) = ContributionResult.Success
    }

    private class NoOpListingRepoForDecoder : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
        override suspend fun getContributions(): List<Ability.Listing> = emptyList()
        override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }

    private class NoOpBuildRepoForDecoder : CharacterBuildRepository {
        override val builds: Flow<List<CharacterBuild>> = MutableStateFlow(emptyList())
        override suspend fun getBuilds() = emptyList<CharacterBuild>()
        override suspend fun getBuild(name: String): CharacterBuild? = null
        override suspend fun saveBuildContribution(build: CharacterBuild) = ContributionResult.Success
        override suspend fun deleteContribution(name: String) = ContributionResult.Success
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

    private class RecordingEssenceRepo(private val data: List<Essence>) : EssenceRepository {
        val combinationsAdded = mutableListOf<Pair<Essence.Confluence, ConfluenceSet>>()
        override val essences: Flow<List<Essence>> = MutableStateFlow(data)
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences() = data
        override suspend fun getContributions() = emptyList<Essence>()
        override suspend fun getConflicts() = emptyList<EssenceConflict>()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
        override suspend fun saveConfluenceContribution(confluence: Essence.Confluence, referencedManifestations: List<Essence.Manifestation>) = ContributionResult.Success
        override suspend fun addCombinationToConfluence(target: Essence.Confluence, combination: ConfluenceSet): ContributionResult {
            combinationsAdded.add(target to combination)
            return ContributionResult.Success
        }
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
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing) = ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String) = wizardry.compendium.repositories.DeleteImpact()
    }
}
