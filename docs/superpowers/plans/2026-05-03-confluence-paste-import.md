# Confluence contribute-screen paste-import — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add paste-import to the Confluence tab of the Essence contribute screen. Pasting a single-confluence share opens a non-dismissible read-only review sheet; the user takes the import as-is or cancels.

**Architecture:** New `decodeConfluenceBundle` on `ShareViewModel` builds a flat preview model. `EssenceContributionsViewModel` gains a `pasteImportState` flow and delegates the actual save to the existing `WireImporter`. A new `ConfluenceReviewSheet` composable renders the preview as a `ModalBottomSheet`. Snackbar feedback is hosted locally by the contribute screen.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.coroutines, JUnit 4, kotlinx.coroutines.test (already in use across the project).

**Spec:** `docs/superpowers/specs/2026-05-03-confluence-paste-import-design.md`

---

## File structure

**Modify:**
- `app/src/main/java/wizardry/compendium/share/ShareViewModel.kt` — add preview data classes + `decodeConfluenceBundle` suspend method.
- `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModel.kt` — add `PasteImportState` sealed interface, `pasteImportState` flow, `startPasteImport` / `cancelPasteImport` / `confirmPasteImport` / `consumePasteImportTerminal` methods, inject `WireImporter` (constructed inline from existing repos).
- `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsScreen.kt` — add new `onPasteImportConfluence` suspend callback param; new "Import from Share" button on Confluence tab; paste dialog; render the review sheet; SnackbarHost; success / failure surfacing.
- `app/src/main/java/wizardry/compendium/MainActivity.kt:204-217` — wire `onPasteImportConfluence` to `shareViewModel.decodeConfluenceBundle`.

**Create:**
- `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/ConfluenceReviewSheet.kt` — new stateless composable rendering the preview, with `@Preview`s.
- `app/src/test/java/wizardry/compendium/share/ShareViewModelDecodeConfluenceBundleTest.kt` — decode tests.
- `essence-contributions/src/test/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModelPasteImportTest.kt` — paste-import lifecycle tests.

---

## Task 1: Preview data classes and `ShareViewModel.decodeConfluenceBundle`

**Files:**
- Modify: `app/src/main/java/wizardry/compendium/share/ShareViewModel.kt`
- Test: `app/src/test/java/wizardry/compendium/share/ShareViewModelDecodeConfluenceBundleTest.kt`

The preview is a flat data class meant for UI consumption. `unresolvableNames` is a top-level `Set<String>` (case-insensitive matching, lowercase-stored) computed from combination references that are neither bundled nor in the receiver's DB.

`decodeConfluenceBundle` is `suspend` because it reads `essenceRepository.getEssences()` (which is suspend per `EssenceRepository.kt:12`).

- [ ] **Step 1: Write the failing test for the happy path**

Create `app/src/test/java/wizardry/compendium/share/ShareViewModelDecodeConfluenceBundleTest.kt`:

```kotlin
package wizardry.compendium.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Conflict
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelDecodeConfluenceBundleTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var essenceRepo: FakeEssenceRepository
    private lateinit var stoneRepo: FakeAwakeningStoneRepository
    private lateinit var listingRepo: FakeAbilityListingRepository
    private lateinit var viewModel: ShareViewModel

    private val wind = Essence.of("Wind", "", Rarity.Common, false)
    private val blood = Essence.of("Blood", "", Rarity.Uncommon, false)
    private val sin = Essence.of("Sin", "", Rarity.Legendary, false)
    private val doom = Essence.of(
        name = "Doom",
        restricted = false,
        ConfluenceSet(wind, blood, sin),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = FakeEssenceRepository(canonical = emptyList(), contributions = emptyList())
        stoneRepo = FakeAwakeningStoneRepository()
        listingRepo = FakeAbilityListingRepository()
        viewModel = ShareViewModel(essenceRepo, stoneRepo, listingRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `decodes a bundled confluence with all-new essences`() = runTest(dispatcher) {
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
        val text = EnvelopeCodec.encode(exporter.exportSingle(doom)).text

        val result = viewModel.decodeConfluenceBundle(text)

        assertTrue(result is ShareViewModel.DecodedSingle.Loaded)
        val preview = (result as ShareViewModel.DecodedSingle.Loaded).model
        assertEquals("Doom", preview.confluenceName)
        assertEquals(false, preview.isRestricted)
        assertEquals(1, preview.combinations.size)
        assertEquals(setOf("Wind", "Blood", "Sin"), preview.essences.map { it.name }.toSet())
        assertTrue(preview.essences.all { it.isNew })
        assertTrue(preview.unresolvableNames.isEmpty())
    }
}
```

You'll also need fake repositories. Add at the bottom of the same file (cribbed from `ConflictsViewModelTest.kt:122-184` and adjusted for these tests):

```kotlin
private class FakeEssenceRepository(
    canonical: List<Essence> = emptyList(),
    contributions: List<Essence> = emptyList(),
) : EssenceRepository {
    private val all: List<Essence> = canonical + contributions
    val savedManifestations = mutableListOf<Essence.Manifestation>()
    val savedConfluences = mutableListOf<Essence.Confluence>()

    override val essences: Flow<List<Essence>> = MutableStateFlow(all)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = all
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult {
        savedManifestations += manifestation
        return ContributionResult.Success
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult {
        savedConfluences += confluence
        return ContributionResult.Success
    }
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
}

private class FakeAwakeningStoneRepository : AwakeningStoneRepository {
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

private class FakeAbilityListingRepository : AbilityListingRepository {
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
```

Add imports: `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.MutableStateFlow`, plus `wizardry.compendium.essences.AbilityListingConflict`, `wizardry.compendium.essences.AwakeningStoneConflict`, `wizardry.compendium.essences.EssenceConflict`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "wizardry.compendium.share.ShareViewModelDecodeConfluenceBundleTest.decodes a bundled confluence with all-new essences"`

Expected: FAIL — `decodeConfluenceBundle` is unresolved.

- [ ] **Step 3: Add the preview data classes to `ShareViewModel.kt`**

Insert below `DecodedSingle` (around line 68):

```kotlin
/**
 * Snapshot of a pasted confluence-bundle ready to render in the
 * read-only review sheet.
 *
 * `unresolvableNames` is the set of essence names referenced by combinations
 * that are neither bundled in the share nor present in the receiver's
 * library. Names are stored lowercase. The Save button is disabled when
 * this set is non-empty.
 */
data class ConfluenceImportPreview(
    val envelope: wizardry.compendium.wire.Envelope,
    val confluenceName: String,
    val isRestricted: Boolean,
    val combinations: List<PreviewCombination>,
    val essences: List<PreviewEssence>,
    val unresolvableNames: Set<String>,
)

data class PreviewCombination(
    val essence1: String,
    val essence2: String,
    val essence3: String,
    val isRestricted: Boolean,
)

data class PreviewEssence(
    val name: String,
    val rarity: wizardry.compendium.essences.model.Rarity,
    val isNew: Boolean,
)
```

- [ ] **Step 4: Hold a reference to `essenceRepository` so `decodeConfluenceBundle` can read it**

Replace the constructor block at `ShareViewModel.kt:36-46`:

```kotlin
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
    awakeningStoneRepository: AwakeningStoneRepository,
    abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    private val exporter = WireExporter(
        essenceRepository,
        awakeningStoneRepository,
        abilityListingRepository,
    )
```

(`essenceRepository` was previously not held; making it a private val gives `decodeConfluenceBundle` access.)

- [ ] **Step 5: Add `decodeConfluenceBundle` to `ShareViewModel`**

Insert below `decodeSingleManifestation` (after the existing line 117):

```kotlin
/**
 * Decode a paste containing exactly one confluence (with its bundled
 * essences) and build a preview for the review sheet.
 *
 * Suspends because it reads the receiver's current essences to compute
 * `isNew` per bundled essence and `unresolvableNames` for combination
 * references that aren't bundled and aren't in the DB.
 */
suspend fun decodeConfluenceBundle(text: String): DecodedSingle<ConfluenceImportPreview> {
    if (text.isBlank()) return DecodedSingle.Failed("Paste is empty.")
    val envelope = try {
        EnvelopeCodec.decode(text)
    } catch (e: WireVersionUnsupported) {
        return DecodedSingle.Failed("This share was made with a newer app version. Update to import.")
    } catch (e: WireDecodeException) {
        return DecodedSingle.Failed(e.message ?: "Pasted data is not a valid contribution share.")
    } catch (e: Exception) {
        return DecodedSingle.Failed("Import failed: ${e.message}")
    }

    val others = envelope.stones.size + envelope.listings.size
    if (envelope.confluences.size != 1 || others > 0) {
        return DecodedSingle.Failed(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
        )
    }

    val wireConfluence = envelope.confluences.single()
    val bundledEssences = envelope.manifestations.map { EnvelopeMapper.toModel(it) }
    val bundledByLower = bundledEssences.associateBy { it.name.lowercase() }

    val dbByLower = essenceRepository.getEssences()
        .filterIsInstance<Essence.Manifestation>()
        .associateBy { it.name.lowercase() }

    val previewEssences = bundledEssences.map { e ->
        PreviewEssence(
            name = e.name,
            rarity = e.rarity,
            isNew = !dbByLower.containsKey(e.name.lowercase()),
        )
    }

    val combinations = wireConfluence.combinations.map { set ->
        PreviewCombination(
            essence1 = set.name1,
            essence2 = set.name2,
            essence3 = set.name3,
            isRestricted = set.restrictedFlag != 0,
        )
    }

    val referencedNamesLower = combinations
        .flatMap { listOf(it.essence1, it.essence2, it.essence3) }
        .map { it.lowercase() }
        .toSet()

    val unresolvable = referencedNamesLower.filter { lower ->
        !bundledByLower.containsKey(lower) && !dbByLower.containsKey(lower)
    }.toSet()

    return DecodedSingle.Loaded(
        ConfluenceImportPreview(
            envelope = envelope,
            confluenceName = wireConfluence.name,
            isRestricted = wireConfluence.isRestricted,
            combinations = combinations,
            essences = previewEssences,
            unresolvableNames = unresolvable,
        ),
    )
}
```

**Verified field names** (from `wire/src/main/java/wizardry/compendium/wire/Envelope.kt`):
`wire.Confluence.combinations: List<wire.ConfluenceSet>`,
`wire.ConfluenceSet.name1` / `name2` / `name3` / `restrictedFlag: Int` (0 = false, 1 = true).

- [ ] **Step 6: Run the happy-path test — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "wizardry.compendium.share.ShareViewModelDecodeConfluenceBundleTest.decodes a bundled confluence with all-new essences"`

Expected: PASS.

- [ ] **Step 7: Add the remaining decode tests**

Append to `ShareViewModelDecodeConfluenceBundleTest`:

```kotlin
@Test
fun `marks bundled essences as not new when already in repo`() = runTest(dispatcher) {
    essenceRepo = FakeEssenceRepository(canonical = listOf(wind), contributions = emptyList())
    viewModel = ShareViewModel(essenceRepo, stoneRepo, listingRepo)
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val text = EnvelopeCodec.encode(exporter.exportSingle(doom)).text

    val preview = (viewModel.decodeConfluenceBundle(text) as ShareViewModel.DecodedSingle.Loaded).model

    val newness = preview.essences.associate { it.name to it.isNew }
    assertEquals(false, newness["Wind"])
    assertEquals(true, newness["Blood"])
    assertEquals(true, newness["Sin"])
}

@Test
fun `flags unresolvable combination references`() = runTest(dispatcher) {
    // Build an envelope with a confluence whose combination references "Phantom",
    // but Phantom is neither bundled nor in the repo.
    val phantomReferencingConfluence = Essence.of(
        name = "Mirage",
        restricted = false,
        ConfluenceSet(
            wind,
            blood,
            Essence.of("Phantom", "", Rarity.Rare, false),
        ),
    )
    // Re-bundle but strip the Phantom essence from the wire envelope.
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val full = exporter.exportSingle(phantomReferencingConfluence)
    val stripped = full.copy(
        manifestations = full.manifestations.filter { it.name != "Phantom" },
    )
    val text = EnvelopeCodec.encode(stripped).text

    val preview = (viewModel.decodeConfluenceBundle(text) as ShareViewModel.DecodedSingle.Loaded).model

    assertEquals(setOf("phantom"), preview.unresolvableNames)
}

@Test
fun `rejects empty paste`() = runTest(dispatcher) {
    val result = viewModel.decodeConfluenceBundle("")
    assertEquals("Paste is empty.", (result as ShareViewModel.DecodedSingle.Failed).reason)
}

@Test
fun `rejects malformed paste`() = runTest(dispatcher) {
    val result = viewModel.decodeConfluenceBundle("not-a-real-share")
    assertTrue(result is ShareViewModel.DecodedSingle.Failed)
}

@Test
fun `rejects envelope with zero confluences`() = runTest(dispatcher) {
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val text = EnvelopeCodec.encode(exporter.exportSingle(wind)).text
    val result = viewModel.decodeConfluenceBundle(text)
    assertEquals(
        "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
        (result as ShareViewModel.DecodedSingle.Failed).reason,
    )
}

@Test
fun `rejects envelope with two confluences`() = runTest(dispatcher) {
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val a = exporter.exportSingle(doom)
    val tempest = Essence.of(
        name = "Tempest",
        restricted = false,
        ConfluenceSet(wind, blood, sin),
    )
    val b = exporter.exportSingle(tempest)
    val combined = a.copy(confluences = a.confluences + b.confluences)
    val text = EnvelopeCodec.encode(combined).text

    val result = viewModel.decodeConfluenceBundle(text)
    assertTrue(result is ShareViewModel.DecodedSingle.Failed)
}

@Test
fun `rejects envelope containing a stone`() = runTest(dispatcher) {
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val confluenceEnv = exporter.exportSingle(doom)
    val stone = wizardry.compendium.essences.model.AwakeningStone.of("Volcano", Rarity.Epic)
    val stoneEnv = exporter.exportSingle(stone)
    val combined = confluenceEnv.copy(stones = stoneEnv.stones)
    val text = EnvelopeCodec.encode(combined).text

    val result = viewModel.decodeConfluenceBundle(text)
    assertTrue(result is ShareViewModel.DecodedSingle.Failed)
}
```

**Verified factory signatures**:
- `Essence.of(name: String, description: String, rarity: Rarity, restricted: Boolean): Manifestation`
- `Essence.of(name: String, restricted: Boolean, vararg confluences: ConfluenceSet): Confluence`
- `AwakeningStone.of(name: String, rarity: Rarity): AwakeningStone` (used in `ConflictsViewModelTest.kt:195`)
- `Envelope` is a `data class` so `.copy(stones = ..., confluences = ...)` works.

- [ ] **Step 8: Run the full test class — verify all pass**

Run: `./gradlew :app:testDebugUnitTest --tests "wizardry.compendium.share.ShareViewModelDecodeConfluenceBundleTest"`

Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/wizardry/compendium/share/ShareViewModel.kt \
        app/src/test/java/wizardry/compendium/share/ShareViewModelDecodeConfluenceBundleTest.kt
git commit -m "$(cat <<'EOF'
add ShareViewModel.decodeConfluenceBundle for paste-import preview

ConfluenceImportPreview wraps the decoded envelope with a UI-shaped
projection: per-essence isNew flags computed against the receiver's
current library, plus an unresolvableNames set for combination
references that aren't bundled and aren't in the DB.
EOF
)"
```

---

## Task 2: `EssenceContributionsViewModel` paste-import lifecycle

**Files:**
- Modify: `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModel.kt`
- Test: `essence-contributions/src/test/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModelPasteImportTest.kt`

The VM gains a `pasteImportState` flow + four lifecycle methods. `WireImporter` requires all three repos, but `EssenceContributionsViewModel` currently only injects `EssenceRepository` + `SavedStateHandle` (verified at `EssenceContributionsViewModel.kt:19-22`). Add `AwakeningStoneRepository` and `AbilityListingRepository` as new constructor params; Hilt will resolve them. Construct `WireImporter` inline as a private val (same pattern as `SettingsViewModel.kt:40`).

- [ ] **Step 1: Write the failing test for the happy path**

Create `essence-contributions/src/test/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModelPasteImportTest.kt`:

```kotlin
package wizardry.compendium.essence.contributions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import wizardry.compendium.share.ConfluenceImportPreview
import wizardry.compendium.share.PreviewCombination
import wizardry.compendium.share.PreviewEssence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceContributionsViewModelPasteImportTest {

    private val dispatcher = StandardTestDispatcher()
    // Repos: copy/paste the FakeEssenceRepository / FakeAwakeningStoneRepository /
    // FakeAbilityListingRepository skeletons from ShareViewModelDecodeConfluenceBundleTest
    // (or extract to a shared test util file under app/src/test/.../testutil/ —
    // either is fine).
    private lateinit var essenceRepo: FakeEssenceRepository
    private lateinit var stoneRepo: FakeAwakeningStoneRepository
    private lateinit var listingRepo: FakeAbilityListingRepository
    private lateinit var viewModel: EssenceContributionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = FakeEssenceRepository()
        stoneRepo = FakeAwakeningStoneRepository()
        listingRepo = FakeAbilityListingRepository()
        viewModel = EssenceContributionsViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            essenceRepository = essenceRepo,
            awakeningStoneRepository = stoneRepo,
            abilityListingRepository = listingRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun samplePreview(envelope: Envelope, confluenceName: String = "Doom") =
        ConfluenceImportPreview(
            envelope = envelope,
            confluenceName = confluenceName,
            isRestricted = false,
            combinations = listOf(PreviewCombination("Wind", "Blood", "Sin", false)),
            essences = listOf(
                PreviewEssence("Wind", wizardry.compendium.essences.model.Rarity.Common, true),
                PreviewEssence("Blood", wizardry.compendium.essences.model.Rarity.Uncommon, true),
                PreviewEssence("Sin", wizardry.compendium.essences.model.Rarity.Legendary, true),
            ),
            unresolvableNames = emptySet(),
        )

    @Test
    fun `startPasteImport transitions Idle to Reviewing`() = runTest(dispatcher) {
        // Build a real envelope to satisfy any inner validation.
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
        // ... build a confluence envelope; if the VM doesn't validate the envelope
        // contents inside startPasteImport, an empty Envelope is fine.
        val preview = samplePreview(envelope = Envelope(version = EnvelopeCodec.CurrentVersion))

        viewModel.startPasteImport(preview)
        advanceUntilIdle()

        assertEquals(
            EssenceContributionsViewModel.PasteImportState.Reviewing(preview),
            viewModel.pasteImportState.value,
        )
    }
}
```

Reuse the same `Fake*` skeletons from Task 1's test (or extract them to a shared `app/src/test/.../testutil/Fakes.kt` file — your call; either is fine since the tests live in different modules and shared test util across modules requires extra Gradle plumbing the project may not yet have).

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :essence-contributions:testDebugUnitTest --tests "wizardry.compendium.essence.contributions.EssenceContributionsViewModelPasteImportTest"`

Expected: FAIL — `PasteImportState` / `pasteImportState` / `startPasteImport` unresolved.

- [ ] **Step 3: Add the `PasteImportState` sealed interface to `EssenceContributionsViewModel`**

Insert next to the existing `SaveState` declaration (around line 187-193). Match the project's convention for sibling sealed interfaces:

```kotlin
sealed interface PasteImportState {
    data object Idle : PasteImportState
    data class Reviewing(val preview: wizardry.compendium.share.ConfluenceImportPreview) : PasteImportState
    data class Saving(val preview: wizardry.compendium.share.ConfluenceImportPreview) : PasteImportState
    data class Done(
        val summary: wizardry.compendium.wire.ImportSummary,
        val confluenceName: String,
    ) : PasteImportState
    data class Failed(val reason: String) : PasteImportState
}
```

- [ ] **Step 4: Expand the constructor with the two new repos**

Replace the constructor at `EssenceContributionsViewModel.kt:19-22`:

```kotlin
@HiltViewModel
class EssenceContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: wizardry.compendium.essences.AwakeningStoneRepository,
    private val abilityListingRepository: wizardry.compendium.essences.AbilityListingRepository,
) : ViewModel() {
```

Hilt resolves the two new repos automatically since they're already provided across the app (verify via `SettingsViewModel.kt` — it injects all three from the same Hilt module).

- [ ] **Step 5: Add the `pasteImportState` flow + lifecycle methods**

In the VM body (mirroring the existing `saveState` flow, find that and add this nearby):

```kotlin
private val _pasteImportState = MutableStateFlow<PasteImportState>(PasteImportState.Idle)
val pasteImportState: StateFlow<PasteImportState> = _pasteImportState.asStateFlow()

private val wireImporter = wizardry.compendium.wire.WireImporter(
    essenceRepository,
    awakeningStoneRepository,
    abilityListingRepository,
)

fun startPasteImport(preview: wizardry.compendium.share.ConfluenceImportPreview) {
    _pasteImportState.value = PasteImportState.Reviewing(preview)
}

fun cancelPasteImport() {
    _pasteImportState.value = PasteImportState.Idle
}

fun confirmPasteImport() {
    val current = _pasteImportState.value
    if (current !is PasteImportState.Reviewing) return
    _pasteImportState.value = PasteImportState.Saving(current.preview)
    viewModelScope.launch {
        try {
            val summary = wireImporter.import(current.preview.envelope)
            _pasteImportState.value = PasteImportState.Done(summary, current.preview.confluenceName)
        } catch (e: Exception) {
            _pasteImportState.value = PasteImportState.Failed("Import failed: ${e.message}")
        }
    }
}

fun consumePasteImportTerminal() {
    val current = _pasteImportState.value
    if (current is PasteImportState.Done || current is PasteImportState.Failed) {
        _pasteImportState.value = PasteImportState.Idle
    }
}
```

Add imports at the top: `kotlinx.coroutines.flow.MutableStateFlow`, `StateFlow`, `asStateFlow`, `androidx.lifecycle.viewModelScope`, `kotlinx.coroutines.launch` (most likely already present).

- [ ] **Step 6: Run the happy-path test — verify it passes**

Run: `./gradlew :essence-contributions:testDebugUnitTest --tests "wizardry.compendium.essence.contributions.EssenceContributionsViewModelPasteImportTest.startPasteImport transitions Idle to Reviewing"`

Expected: PASS.

- [ ] **Step 7: Add the remaining lifecycle tests**

Append:

```kotlin
@Test
fun `cancelPasteImport returns to Idle`() = runTest(dispatcher) {
    val preview = samplePreview(Envelope(version = EnvelopeCodec.CurrentVersion))
    viewModel.startPasteImport(preview)
    viewModel.cancelPasteImport()
    advanceUntilIdle()
    assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
}

@Test
fun `confirmPasteImport runs through Saving to Done with summary`() = runTest(dispatcher) {
    val confluence = wizardry.compendium.essences.model.Essence.of(
        name = "Doom",
        restricted = false,
        wizardry.compendium.essences.model.ConfluenceSet(
            wizardry.compendium.essences.model.Essence.of("Wind", "", wizardry.compendium.essences.model.Rarity.Common, false),
            wizardry.compendium.essences.model.Essence.of("Blood", "", wizardry.compendium.essences.model.Rarity.Uncommon, false),
            wizardry.compendium.essences.model.Essence.of("Sin", "", wizardry.compendium.essences.model.Rarity.Legendary, false),
        ),
    )
    val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo)
    val envelope = exporter.exportSingle(confluence)
    val preview = samplePreview(envelope)

    viewModel.startPasteImport(preview)
    viewModel.confirmPasteImport()
    advanceUntilIdle()

    val state = viewModel.pasteImportState.value
    assertTrue(state is EssenceContributionsViewModel.PasteImportState.Done)
    val done = state as EssenceContributionsViewModel.PasteImportState.Done
    assertEquals("Doom", done.confluenceName)
    // Three essences + one confluence Added
    val addedNames = done.summary.added.map { it.name }
    assertTrue(addedNames.containsAll(listOf("Wind", "Blood", "Sin", "Doom")))
}

@Test
fun `consumePasteImportTerminal clears Done back to Idle`() = runTest(dispatcher) {
    val preview = samplePreview(Envelope(version = EnvelopeCodec.CurrentVersion))
    viewModel.startPasteImport(preview)
    viewModel.confirmPasteImport()
    advanceUntilIdle()
    viewModel.consumePasteImportTerminal()
    assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
}

@Test
fun `confirmPasteImport is a no-op outside Reviewing`() = runTest(dispatcher) {
    viewModel.confirmPasteImport()
    advanceUntilIdle()
    assertEquals(EssenceContributionsViewModel.PasteImportState.Idle, viewModel.pasteImportState.value)
}
```

The `confirmPasteImport runs through Saving to Done` test relies on the fakes' `saveManifestationContribution` / `saveConfluenceContribution` returning `ContributionResult.Success`. Tweak the fakes if your variant defaults differently.

- [ ] **Step 8: Run all VM paste-import tests — verify they pass**

Run: `./gradlew :essence-contributions:testDebugUnitTest --tests "wizardry.compendium.essence.contributions.EssenceContributionsViewModelPasteImportTest"`

Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModel.kt \
        essence-contributions/src/test/java/wizardry/compendium/essence/contributions/EssenceContributionsViewModelPasteImportTest.kt
git commit -m "$(cat <<'EOF'
add PasteImportState lifecycle to EssenceContributionsViewModel

Idle / Reviewing / Saving / Done / Failed states drive the review
sheet. confirmPasteImport delegates to WireImporter (constructed
inline from the same repos already injected, matching the
SettingsViewModel pattern).
EOF
)"
```

---

## Task 3: `ConfluenceReviewSheet` composable

**Files:**
- Create: `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/ConfluenceReviewSheet.kt`

Stateless composable. Renders the preview as a column inside the sheet's content slot. Caller owns the `ModalBottomSheet`.

User-facing strings always say "Essence", never "Manifestation".

- [ ] **Step 1: Create the file with the composable**

Create `ConfluenceReviewSheet.kt`:

```kotlin
package wizardry.compendium.essence.contributions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.share.ConfluenceImportPreview
import wizardry.compendium.share.PreviewCombination
import wizardry.compendium.share.PreviewEssence

@Composable
fun ConfluenceReviewSheet(
    preview: ConfluenceImportPreview,
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val unresolvable = preview.unresolvableNames
    val canSave = !saving && unresolvable.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import Confluence", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preview.confluenceName, style = MaterialTheme.typography.headlineSmall)
            if (preview.isRestricted) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, enabled = false, label = { Text("Restricted") })
            }
        }

        Text("Combinations", style = MaterialTheme.typography.titleMedium)
        preview.combinations.forEach { combo ->
            CombinationRow(combo, unresolvable)
        }

        if (preview.essences.isNotEmpty()) {
            Text("Essences in this share", style = MaterialTheme.typography.titleMedium)
            preview.essences.forEach { essence ->
                EssenceRow(essence)
            }
        }

        if (unresolvable.isNotEmpty()) {
            Text(
                text = "This share references essences that aren't in your library. " +
                    "Cancel and import the essences first.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun CombinationRow(combo: PreviewCombination, unresolvable: Set<String>) {
    val parts = listOf(combo.essence1, combo.essence2, combo.essence3)
    Row(verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { idx, name ->
            if (idx > 0) Text(" + ")
            val isUnresolvable = unresolvable.contains(name.lowercase())
            Text(
                text = if (isUnresolvable) "$name (unknown)" else name,
                color = if (isUnresolvable) MaterialTheme.colorScheme.error else Color.Unspecified,
                fontWeight = if (isUnresolvable) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (combo.isRestricted) {
            Text("  (restricted)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EssenceRow(essence: PreviewEssence) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${essence.name} (${essence.rarity.name.lowercase()})")
        if (essence.isNew) {
            AssistChip(onClick = {}, enabled = false, label = { Text("New") })
        } else {
            AssistChip(onClick = {}, enabled = false, label = { Text("Already in your library") })
        }
    }
}

// region Previews

private val previewTypical = ConfluenceImportPreview(
    envelope = wizardry.compendium.wire.Envelope(version = wizardry.compendium.wire.EnvelopeCodec.CurrentVersion),
    confluenceName = "Doom",
    isRestricted = false,
    combinations = listOf(PreviewCombination("Sin", "Blood", "Dark", false)),
    essences = listOf(
        PreviewEssence("Sin", Rarity.Legendary, isNew = true),
        PreviewEssence("Blood", Rarity.Uncommon, isNew = false),
        PreviewEssence("Dark", Rarity.Rare, isNew = true),
    ),
    unresolvableNames = emptySet(),
)

private val previewUnresolvable = previewTypical.copy(
    combinations = listOf(PreviewCombination("Sin", "Phantom", "Dark", false)),
    unresolvableNames = setOf("phantom"),
)

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetTypicalPreview() {
    ConfluenceReviewSheet(preview = previewTypical, saving = false, onSave = {}, onCancel = {})
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetUnresolvablePreview() {
    ConfluenceReviewSheet(preview = previewUnresolvable, saving = false, onSave = {}, onCancel = {})
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetSavingPreview() {
    ConfluenceReviewSheet(preview = previewTypical, saving = true, onSave = {}, onCancel = {})
}

// endregion
```

- [ ] **Step 2: Build the module to check compilation**

Run: `./gradlew :essence-contributions:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add essence-contributions/src/main/java/wizardry/compendium/essence/contributions/ConfluenceReviewSheet.kt
git commit -m "$(cat <<'EOF'
add ConfluenceReviewSheet composable

Stateless review surface for confluence paste-import. Highlights
unresolvable essence references in error color, disables Save when
any are present, and shows progress on Save during import.
EOF
)"
```

---

## Task 4: Wire the Confluence-tab Import button, paste dialog, review sheet, and snackbar

**Files:**
- Modify: `essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsScreen.kt`

The screen gains:
- A new callback param `onPasteImportConfluence: suspend (text: String) -> ShareViewModel.DecodedSingle<ConfluenceImportPreview>`.
- An "Import from Share" button at the bottom of `ConfluenceForm` (Create mode only).
- A paste `AlertDialog` for the confluence flow (separate from the existing essence paste dialog — same shape, different state vars).
- A `ModalBottomSheet` that shows when `pasteImportState` is `Reviewing` or `Saving`. `confirmValueChange = { false }` blocks drag-dismiss.
- A `SnackbarHost` wrapped via a `Scaffold` outer layout (the screen currently has no scaffold).
- A `LaunchedEffect(pasteImportState)` to surface `Done` (snackbar) and `Failed` (error dialog), then call `consumePasteImportTerminal()`.

- [ ] **Step 1: Add the new callback param**

Edit the `EssenceContributionsScreen` signature at `EssenceContributionsScreen.kt:27-37` to add the new parameter:

```kotlin
@Composable
fun EssenceContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    onPasteImport: (text: String) -> Pair<Essence.Manifestation?, String?> = { null to null },
    /**
     * Suspend decoder for confluence paste-bundle. Wired in MainActivity
     * to ShareViewModel.decodeConfluenceBundle. Returns a preview to drive
     * the review sheet, or a Failed message to surface in the error dialog.
     */
    onPasteImportConfluence: suspend (text: String) -> wizardry.compendium.share.ShareViewModel.DecodedSingle<wizardry.compendium.share.ConfluenceImportPreview> =
        { wizardry.compendium.share.ShareViewModel.DecodedSingle.Failed("not wired") },
    viewModel: EssenceContributionsViewModel = hiltViewModel(),
) {
```

- [ ] **Step 2: Wrap the existing content in a Scaffold with a SnackbarHost**

The screen currently renders content directly. Wrap the entire body in a `Scaffold`. Find the start of the `when (val current = mode)` block at `EssenceContributionsScreen.kt:57` and wrap the existing body so it becomes the scaffold content. At the top of `EssenceContributionsScreen`, declare:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val pasteImportState by viewModel.pasteImportState.collectAsState()
val coroutineScope = rememberCoroutineScope()
var showConfluencePasteDialog by remember { mutableStateOf(false) }
var confluencePasteText by remember { mutableStateOf("") }
var confluenceImportError by remember { mutableStateOf<String?>(null) }
```

Then wrap the body:

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { contentPadding ->
    Box(modifier = Modifier.padding(contentPadding)) {
        // existing when (val current = mode) { ... } block goes here
        // existing AlertDialog blocks for showImportDialog and importErrorMessage stay here
    }
}
```

- [ ] **Step 3: Plumb `onConfluenceImportClick` through `CreateContributions` and `ConfluenceForm`**

`CreateContributions` (line 147) gets a new param `onConfluenceImportClick: () -> Unit`. Pass it from the `Mode.Create` branch:

```kotlin
EssenceContributionsViewModel.Mode.Create -> CreateContributions(
    // ... existing params ...
    onManifestationImportClick = {
        pasteText = ""
        showImportDialog = true
    },
    onConfluenceImportClick = {
        confluencePasteText = ""
        showConfluencePasteDialog = true
    },
)
```

`CreateContributions` then forwards to `ConfluenceForm`:

```kotlin
1 -> ConfluenceForm(
    availableManifestations = availableManifestations,
    availableConfluences = availableConfluences,
    saveState = saveState,
    onSaveNew = onSaveNewConfluence,
    onAddCombination = onAddCombination,
    onClearState = onClearState,
    onImportClick = onConfluenceImportClick,
)
```

`ConfluenceForm` (line 354) gets a new param `onImportClick: () -> Unit`. Add it last in the param list. Add an `OutlinedButton` at the bottom of the form (after the existing Save Confluence button at line 538):

```kotlin
OutlinedButton(
    onClick = onImportClick,
    modifier = Modifier.fillMaxWidth(),
) { Text("Import from Share") }
```

(Strings always say "Essence" / "Confluence", never "Manifestation" — verified.)

- [ ] **Step 4: Add the paste dialog for confluence**

Inside the `Box(modifier = Modifier.padding(contentPadding))` block, after the existing essence paste dialog and error dialog, add:

```kotlin
if (showConfluencePasteDialog) {
    AlertDialog(
        onDismissRequest = { showConfluencePasteDialog = false },
        title = { Text("Import Confluence") },
        text = {
            OutlinedTextField(
                value = confluencePasteText,
                onValueChange = { confluencePasteText = it },
                label = { Text("Paste a single-confluence share") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = {
            Button(onClick = {
                val text = confluencePasteText
                showConfluencePasteDialog = false
                coroutineScope.launch {
                    when (val result = onPasteImportConfluence(text)) {
                        is wizardry.compendium.share.ShareViewModel.DecodedSingle.Loaded ->
                            viewModel.startPasteImport(result.model)
                        is wizardry.compendium.share.ShareViewModel.DecodedSingle.Failed ->
                            confluenceImportError = result.reason
                    }
                }
            }) { Text("Import") }
        },
        dismissButton = {
            OutlinedButton(onClick = { showConfluencePasteDialog = false }) { Text("Cancel") }
        },
    )
}

confluenceImportError?.let { message ->
    AlertDialog(
        onDismissRequest = { confluenceImportError = null },
        title = { Text("Couldn't import") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = { confluenceImportError = null }) { Text("OK") }
        },
    )
}
```

- [ ] **Step 5: Render the review sheet for `Reviewing` and `Saving`**

Inside the `Box`, add:

```kotlin
val reviewing = pasteImportState as? EssenceContributionsViewModel.PasteImportState.Reviewing
val savingState = pasteImportState as? EssenceContributionsViewModel.PasteImportState.Saving
val activePreview = reviewing?.preview ?: savingState?.preview
val isSaving = savingState != null

if (activePreview != null) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    ModalBottomSheet(
        onDismissRequest = { /* drag-dismiss is blocked by confirmValueChange */ },
        sheetState = sheetState,
    ) {
        ConfluenceReviewSheet(
            preview = activePreview,
            saving = isSaving,
            onSave = { viewModel.confirmPasteImport() },
            onCancel = { viewModel.cancelPasteImport() },
        )
    }
}
```

- [ ] **Step 6: Surface `Done` and `Failed` via LaunchedEffect**

Add in the screen body, alongside the existing `LaunchedEffect(saveState)`:

```kotlin
LaunchedEffect(pasteImportState) {
    when (val state = pasteImportState) {
        is EssenceContributionsViewModel.PasteImportState.Done -> {
            val summary = state.summary
            val confluenceFailed = summary.results
                .filterIsInstance<wizardry.compendium.wire.ImportResult.Failed>()
                .firstOrNull { it.domain == wizardry.compendium.wire.ImportResult.Domain.Essence && it.name == state.confluenceName }
            val confluenceSkipped = summary.results
                .filterIsInstance<wizardry.compendium.wire.ImportResult.SkippedDuplicate>()
                .firstOrNull { it.domain == wizardry.compendium.wire.ImportResult.Domain.Essence && it.name == state.confluenceName }
            val essencesAdded = summary.added.count {
                it.domain == wizardry.compendium.wire.ImportResult.Domain.Essence && it.name != state.confluenceName
            }
            val message = when {
                confluenceFailed != null -> null  // surface via dialog instead, see below
                confluenceSkipped != null -> buildString {
                    append("Confluence '").append(state.confluenceName).append("' is already in your library")
                    if (essencesAdded > 0) append(" (added ").append(essencesAdded).append(" essence")
                        .append(if (essencesAdded == 1) "" else "s").append(")")
                }
                else -> buildString {
                    append("Imported confluence '").append(state.confluenceName).append("'")
                    if (essencesAdded > 0) append(" (added ").append(essencesAdded).append(" essence")
                        .append(if (essencesAdded == 1) "" else "s").append(")")
                }
            }
            if (message != null) {
                snackbarHostState.showSnackbar(message)
                viewModel.consumePasteImportTerminal()
            } else {
                confluenceImportError = confluenceFailed?.reason ?: "Failed to import confluence."
                viewModel.consumePasteImportTerminal()
            }
        }
        is EssenceContributionsViewModel.PasteImportState.Failed -> {
            confluenceImportError = state.reason
            viewModel.consumePasteImportTerminal()
        }
        else -> {}
    }
}
```

- [ ] **Step 7: Build to check compilation**

Run: `./gradlew :essence-contributions:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add essence-contributions/src/main/java/wizardry/compendium/essence/contributions/EssenceContributionsScreen.kt
git commit -m "$(cat <<'EOF'
wire confluence paste-import into EssenceContributionsScreen

Adds Import from Share button on the Confluence tab, paste dialog,
non-dismissible review sheet, snackbar success surfacing, and error
dialog for decode/save failures.
EOF
)"
```

---

## Task 5: Wire `onPasteImportConfluence` in MainActivity

**Files:**
- Modify: `app/src/main/java/wizardry/compendium/MainActivity.kt:204-217`

- [ ] **Step 1: Add the callback wiring**

Inside the `composable(Nav.Contributions.route, ...)` block at line 204-217, add `onPasteImportConfluence` next to the existing `onPasteImport`:

```kotlin
EssenceContributionsScreen(
    onContributionSaved = { navController.popBackStack() },
    onContributionDeleted = { navController.popBackStack(Nav.EssenceSearch.route, false) },
    onPasteImport = { text ->
        when (val result = shareViewModel.decodeSingleManifestation(text)) {
            is ShareViewModel.DecodedSingle.Loaded -> result.model to null
            is ShareViewModel.DecodedSingle.Failed -> null to result.reason
        }
    },
    onPasteImportConfluence = { text -> shareViewModel.decodeConfluenceBundle(text) },
)
```

- [ ] **Step 2: Build the app to check compilation**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew test`

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/wizardry/compendium/MainActivity.kt
git commit -m "$(cat <<'EOF'
wire ShareViewModel.decodeConfluenceBundle into the contribute screen

Connects MainActivity's Contributions destination to the new
suspend decode entry point.
EOF
)"
```

---

## Task 6: On-device round-trip verification

**Files:** none — manual verification.

- [ ] **Step 1: Deploy to a connected device**

Per the project's standing rule (`feedback_deploy_after_work.md`), use the `android-cli` skill to install the debug build on the connected USB device.

- [ ] **Step 2: Round-trip test**

1. On the device, contribute a new confluence (e.g., "Tempest" combining 3 user-contributed essences). Use Share to copy the text.
2. Delete the confluence and any contributed essences from the device's library so the receiver state is empty.
3. Go back to Contribute → Confluence tab → tap **Import from Share** → paste the share text → tap Import.
4. The review sheet should open with the confluence name, combinations, and essence rows showing all bundled essences as **New**, no unresolvable references.
5. Tap Save. The sheet closes; a snackbar reads `Imported confluence 'Tempest' (added 3 essences)`.
6. Verify the confluence and essences appear in their respective list screens.

- [ ] **Step 3: Edge-case: existing essence**

1. Re-contribute one of the essences manually first (e.g., "Wind"). Re-paste the same share.
2. Review sheet should mark Wind as **Already in your library**, the other two as **New**.
3. Save → snackbar `Imported confluence 'Tempest' (added 2 essences)`.

- [ ] **Step 4: Edge-case: confluence collides**

1. Save the confluence first. Re-paste the same share.
2. Review sheet opens with all three essences marked **Already in your library**.
3. Save → snackbar `Confluence 'Tempest' is already in your library`.

- [ ] **Step 5: Edge-case: cancel**

1. Paste a fresh share (different confluence). Review sheet opens. Tap **Cancel**.
2. Sheet closes. No new entries appear in either list.
3. Try drag-dismissing the sheet — should be blocked.

- [ ] **Step 6: Edge-case: malformed paste**

1. Paste random text. Error dialog appears with a "Pasted data is not a valid contribution share." message.

- [ ] **Step 7: Mark the TODO item complete**

Update `~/tools/MindPalace/MindPalace/compendium/TODO.md` — flip the unchecked Confluence paste-import item to checked, with a brief note matching the style of the surrounding entries.
