# Conflicts module extraction

## Goal

Extract the conflicts UI from `:app` into a new `:conflicts` Gradle module, matching the shape of the existing per-feature modules (`:essence-contributions`, `:awakening-stone-search`, etc.).

## Motivation

`:app` currently hosts the conflicts screen and viewmodel directly, while every other feature surface lives in its own module. Moving conflicts into `:conflicts` keeps the app module a thin composition root and makes the conflicts feature buildable and testable in isolation.

## Module shape

- Path: `conflicts/` at project root.
- Plugins: `compendium.android`, `alias(libs.plugins.kotlin.compose)`.
- Namespace: `wizardry.compendium.conflicts` — matches the existing package, so MainActivity needs no import changes.
- Compose enabled via `buildFeatures { compose = true }`.
- Standard release `buildTypes` block and META-INF packaging excludes copied from sibling modules.

## Dependencies

Only what the moved source actually references:

- `project(":essences")` — `Conflict`, `EssenceConflict`, `AwakeningStoneConflict`, `AbilityListingConflict`, the three repository interfaces, `Essence.Confluence`, `ConfluenceSet`.
- Compose BOM (`platform(libs.androidx.compose.bom)`) on `implementation`, `testImplementation`, and `androidTestImplementation`.
- `libs.androidx.core.ktx`.
- Compose UI: `androidx.compose.ui`, `androidx.compose.material3`, `androidx.compose.ui.tooling.preview`, `androidx.compose.material.icons.core`, `androidx.navigation.compose`.
- Hilt: `hilt.android` + `ksp(libs.hilt.compiler)`, `androidx.lifecycle.viewmodel.compose`, `androidx.hilt.navigation.compose`.
- Tests: `libs.junit4`, `libs.kotlin.coroutines.test`, `androidx.junit`, `androidx.espresso.core`. Debug: `androidx.compose.ui.tooling`, `androidx.compose.ui.test.manifest`.

`:design`, `:wire`, and `:model-core` are intentionally omitted — the conflicts sources do not reference them. They can be added later if the screen grows to use them.

## File moves

Source files move without edits:

- `app/src/main/java/wizardry/compendium/conflicts/ConflictsScreen.kt` → `conflicts/src/main/java/wizardry/compendium/conflicts/ConflictsScreen.kt`
- `app/src/main/java/wizardry/compendium/conflicts/ConflictsViewModel.kt` → `conflicts/src/main/java/wizardry/compendium/conflicts/ConflictsViewModel.kt`
- `app/src/test/java/wizardry/compendium/conflicts/ConflictsViewModelTest.kt` → `conflicts/src/test/java/wizardry/compendium/conflicts/ConflictsViewModelTest.kt`

The empty `app/src/main/java/wizardry/compendium/conflicts/` and `app/src/test/java/wizardry/compendium/conflicts/` directories should be removed after the moves.

## Build wiring

- `settings.gradle.kts`: add `include(":conflicts")` (alphabetical placement between `:awakening-stone-search` and `:dataloader`).
- `app/build.gradle.kts`: add `implementation(project(":conflicts"))` (alphabetical placement between `:awakening-stone-search` and `:dataloader`).

No other module needs to depend on `:conflicts`; only `:app` consumes it.

## Validation

- `./gradlew :conflicts:test` — `ConflictsViewModelTest` passes from the new module.
- `./gradlew :app:assembleDebug` — app still builds with the new module wiring.
- Deploy to a connected device via the `android-cli` skill and open the conflicts screen as a final smoke check (per project convention to deploy after completing work).

## Out of scope

- No behavior changes to the conflicts UI, viewmodel, or repository contracts.
- No changes to MainActivity navigation wiring beyond what the new module dependency provides at compile time.
- No new tests; the existing viewmodel test moves as-is.
