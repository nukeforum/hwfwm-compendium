# Confluence contribute-screen paste-import — design

Status: design approved, pre-plan
Date: 2026-05-03
Tracking: `~/tools/MindPalace/MindPalace/compendium/TODO.md` → "Confluence contribute-screen paste-import"

## Goal

Allow a user on the Confluence tab of the Essence contribute screen to import
a single-confluence share by pasting it. This closes the last hole in the
contributions paste-import surface: awakening stones and essences (the
`Manifestation` subtype) already support paste-import on their contribute
screens. Confluences have been deferred because the wire form embeds essence
references by name and the existing builder UI can't cleanly resolve those
mid-edit.

## Approach

Read-only review-and-save. Pasting opens a `ModalBottomSheet` that summarizes
exactly what will be written; the user takes the import as-is or cancels.
There is no edit path. If they want to tweak the imported confluence, they
save it first and edit it through the normal edit flow.

This sidesteps the mid-edit resolution problem: the importer (already
correct) decides what to write, and the UI's job is just to surface what's
about to happen.

## UI

### Entry point

A new `OutlinedButton("Import from Share")` at the bottom of `ConfluenceForm`
inside `EssenceContributionsScreen`, visible only in Create mode (mirrors the
existing essence-tab Import button at `EssenceContributionsScreen.kt:342-347`).
Tapping it opens an `AlertDialog` with a paste field plus Import / Cancel
buttons — same shape as the existing essence paste dialog at
`EssenceContributionsScreen.kt:104-132`.

### Review sheet

After a successful decode the paste dialog closes and a `ModalBottomSheet`
opens.

- **Sheet state:** `rememberModalBottomSheetState(skipPartiallyExpanded = true,
  confirmValueChange = { false })`. The `confirmValueChange = { false }`
  rejects drag-to-dismiss. The sheet has no `onDismissRequest` close behavior
  beyond routing the back button to the Cancel handler. Only the explicit
  Cancel button (or a successful Save) closes it.
- **Title:** "Import Confluence"
- **Confluence summary:** name (large), restricted badge if set.
- **Combinations:** each combination shown as `m1 + m2 + m3`, with
  "(restricted)" suffix if the combination is restricted. Any name in the
  combination that is unresolvable (neither bundled in the share nor in the
  receiver's library) is rendered with error emphasis (color + an "(unknown)"
  suffix) so the user can see exactly which references can't be resolved.
- **Essences in this share:** one row per *bundled* essence (rarity-tagged),
  with a status tag:
  - `New` (added emphasis) when the essence isn't in the receiver's library.
  - `Already in your library` (muted) when it is.

  Unresolvable names are not in this section by definition (they're the
  references that have no bundled entry); they only appear inside the
  Combinations rows.
- **Buttons row:** Save (primary) and Cancel (outlined).
- **Validation:** Save is disabled when any combination has at least one
  unresolvable essence reference. A short explanatory line appears at the
  bottom in that case: "This share references essences that aren't in your
  library. Cancel and import the essences first."
- **While saving:** Save shows a `CircularProgressIndicator`; Cancel is
  disabled.

### Terminology

User-facing strings always say "Essence", never "Manifestation". The Kotlin
type is `Essence.Manifestation`, but the section header is "Essences
referenced", not "Manifestations referenced". This applies project-wide and
is already the convention in the existing screens.

### Result surfacing

- **Full success** → snackbar:
  `Imported confluence '<name>' (added N essences)` — the parenthetical is
  omitted when no new essences were added.
- **Confluence skipped (already exists)** → snackbar:
  `Confluence '<name>' is already in your library`. If essences were added
  along the way, append ` (added N essences)`.
- **Confluence Failed** (unresolved reference at save time, or repo
  exception) → AlertDialog with the failure reason from
  `ImportResult.Failed.reason`. Any essences that imported successfully
  remain imported; this matches the existing `WireImporter` "no transaction"
  contract.

## Architecture

### `ShareViewModel.decodeConfluenceBundle(text)` *(new)*

Pure decode + preview-build. Returns
`DecodedSingle<ConfluenceImportPreview>`.

```kotlin
data class ConfluenceImportPreview(
    val envelope: Envelope,            // raw envelope, passed to import on Save
    val confluenceName: String,
    val isRestricted: Boolean,
    val combinations: List<PreviewCombination>,
    val essences: List<PreviewEssence>,
    val unresolvableNames: Set<String>, // referenced names not bundled and not in DB
)

data class PreviewCombination(
    val essence1: String,
    val essence2: String,
    val essence3: String,
    val isRestricted: Boolean,
)

data class PreviewEssence(
    val name: String,
    val rarity: Rarity,
    val isNew: Boolean,
)
```

`unresolvableNames` is computed from the union of all combination references
minus (envelope-bundled essence names ∪ DB essence names), case-insensitive.
The Save button is disabled iff `unresolvableNames` is non-empty.

Validation rules for the envelope shape:

- Exactly one confluence; zero stones; zero ability listings.
- Manifestations may be 0..N. (Exporter bundles all referenced essences, but
  a hand-crafted share that omits them is valid as long as the receiver
  already has them.)

Failure messages mirror the existing `decodeSingleStone` / `decodeSingleListing`
patterns at `ShareViewModel.kt:81-117`.

### `EssenceContributionsViewModel` additions

- New state flow `pasteImportState: StateFlow<PasteImportState>`:

  ```kotlin
  sealed interface PasteImportState {
      data object Idle : PasteImportState
      data class Reviewing(val preview: ConfluenceImportPreview) : PasteImportState
      data class Saving(val preview: ConfluenceImportPreview) : PasteImportState
      data class Done(val summary: ImportSummary, val confluenceName: String) : PasteImportState
      data class Failed(val reason: String) : PasteImportState
  }
  ```

  `Done` and `Failed` are transient — the screen consumes them via a
  `LaunchedEffect` (snackbar / error dialog) and resets to `Idle`.

- New methods:

  ```kotlin
  fun startPasteImport(preview: ConfluenceImportPreview)  // Idle → Reviewing
  fun cancelPasteImport()                                  // Reviewing → Idle
  suspend fun confirmPasteImport()                         // Reviewing → Saving → Done/Failed
  fun consumePasteImportTerminal()                         // Done/Failed → Idle
  ```

- Constructor gets a new `WireImporter` injection. The Hilt module that
  already provides `EssenceRepository`, `AwakeningStoneRepository`,
  `AbilityListingRepository` to `SettingsViewModel` adds a provider for
  `WireImporter` if one doesn't already exist.

### `WireImporter` — unchanged

The importer's manifestations-then-confluences ordering and per-entry
`Added` / `SkippedDuplicate` / `Failed` results are exactly what we need.
`importDecodedConfluence` calls `WireImporter.import(envelope)` and
inspects the resulting `ImportSummary`.

### UI: `ConfluenceReviewSheet` *(new composable)*

In `:essence-contributions`, sibling to `ConfluenceForm`. Stateless —
receives the preview, a `saving: Boolean` flag, and `onSave` / `onCancel`
callbacks. The bottom-sheet plumbing lives in `EssenceContributionsScreen`
alongside the existing manifestation/confluence picker sheets.

## Data flow

### Decode

1. `ShareViewModel.decodeConfluenceBundle(text)` runs the existing pipeline
   (base64 → gzip → JSON → migrators → `Envelope`).
2. Validates envelope shape (Section: ShareViewModel additions).
3. Pulls `essenceRepository.getEssences()`, filters to `Manifestation`,
   lowercase-indexes by name.
4. Decodes each bundled essence wire entry into rarity/name; `isNew = !dbHas(name)`.
5. Walks combinations to compute `unresolvableNames`.
6. Returns `DecodedSingle.Loaded(ConfluenceImportPreview(...))`.

### Save

1. Screen calls `viewModel.confirmPasteImport()`.
2. State → `Saving(preview)`.
3. VM calls `importer.import(preview.envelope)`.
4. State → `Done(summary, confluenceName)`.
5. Screen's `LaunchedEffect(pasteImportState)` fires snackbar/dialog,
   then calls `consumePasteImportTerminal()` → state → `Idle`.
6. The Confluence form's `availableManifestations` and
   `availableConfluences` flows are repository-backed StateFlows; new
   contributions appear automatically. (Verify in implementation; if any
   flow needs a manual refresh poke, fix it as part of the implementation
   plan rather than working around it.)

### Cancel

1. Screen calls `viewModel.cancelPasteImport()`.
2. State → `Idle`. Sheet dismisses. No repo writes. The form is unchanged.

## Error handling

### Decode-time failures

Surface in the existing `importErrorMessage` AlertDialog at
`EssenceContributionsScreen.kt:134-143`.

| Cause                                              | Message                                                                                         |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Empty paste                                        | `"Paste is empty."`                                                                             |
| Malformed wire data                                | `WireDecodeException.message` or `"Pasted data is not a valid contribution share."`             |
| Newer wire version                                 | `"This share was made with a newer app version. Update to import."`                             |
| Wrong shape (multi-entity, has stones/listings, 0) | `"This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares."` |
| Unexpected exception                               | `"Import failed: ${e.message}"`                                                                 |

### Preview-time validation

`unresolvableNames.isNotEmpty()` → Save disabled, explanatory text shown.

### Save-time failures

`WireImporter.import` is exception-safe; per-entry results land in the
`ImportSummary`. We still wrap defensively:

- `Failed` confluence result → AlertDialog via `PasteImportState.Failed`.
- `SkippedDuplicate` confluence → snackbar (not an error).
- Essences that imported successfully along the way stay imported (no
  rollback — `WireImporter` is documented "no transaction").
- Unexpected throw → caught → `PasteImportState.Failed("Import failed:
  ${e.message}")`.

### Concurrency

While `Saving`, Save shows progress and Cancel is disabled. Back button is
also a no-op during this state. The sheet stays mounted until `Done` or
`Failed`.

## Testing

### `ShareViewModelTest.decodeConfluenceBundle` *(new)*

- **Round-trip:** encode a confluence with bundled essences via
  `WireExporter.exportSingle(confluence)` → decode → preview matches.
- **Wrong-shape rejections:** envelope with two confluences → Failed; with a
  stone alongside → Failed; with zero confluences → Failed.
- **`isNew` correctness:** stub `EssenceRepository.getEssences()` to return
  one bundled essence name as already-existing → preview marks that one
  `isNew = false`, others `isNew = true`.
- **Unresolvable reference:** combination references X; X is neither
  bundled nor in the repo → preview's `unresolvableNames` contains X.
- **Decode failure parity:** empty paste, malformed, newer version → Failed
  with the exact message strings from the table above.

### `EssenceContributionsViewModelTest.importDecodedConfluence` *(new)*

- **Happy path:** full bundle → state Idle → Reviewing → Saving → Done with
  summary containing 1 confluence Added + N essences Added.
- **Confluence collides with canonical:** preview valid but confluence name
  matches an existing canonical → Done with summary containing
  `SkippedDuplicate` for the confluence.
- **Mixed:** 2 essences new, 1 existing → SkippedDuplicate for the existing
  essence, Added for the new essences, Added for the confluence.
- **Cancel during Reviewing:** state → Idle, repo never called (verify with
  spy / fake).

### Round-trip / mapper coverage

`EnvelopeMapper.toModel(wire: Confluence, lookup)` already has tests in
`EnvelopeMapperTest.kt:48-93`. No new mapper paths introduced.

### Compose previews

Add `@Preview`s for `ConfluenceReviewSheet` in three states:

- Typical (Reviewing — mix of new and existing essences, no unresolvable).
- Unresolvable (Reviewing — has at least one unresolvable, Save disabled).
- Saving (progress indicator on Save, Cancel disabled).

### Manual on-device verification

Round-trip on a real device: contribute a confluence on device A → Share →
paste into device B's Confluence tab → review sheet → Save → confirm the
confluence and any newly imported essences appear in their list screens.

## Per-domain checklist alignment

This work satisfies the contribute-screen paste-import row of the per-domain
checklist in `docs/contributions-import-export.md` for the confluence
domain:

- Wire types: unchanged.
- Envelope: unchanged.
- Repository: unchanged.
- Mapper: unchanged.
- Exporter: unchanged.
- Importer: unchanged.
- UI surface: **new contribute-screen paste-import** (this work).
- Schema lock: unchanged.
- Tests: new `ShareViewModel` decode tests + `EssenceContributionsViewModel`
  import tests; importer/mapper coverage already present.

## Out of scope

- Paste-import for ability listings on its contribute screen (not currently
  on the TODO; can copy this pattern when desired).
- Per-table export/import in Settings.
- Build / loadout sharing (separate format).
- Edit-after-paste UX. Users who want to edit the imported confluence save
  first and edit through the normal edit flow.
