# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Architecture

- **Entity modules follow a triad**: `<entity>/search`, `<entity>/details`, `<entity>/contributions`
  (see `essence`, `awakening-stone`, `ability-listing`, `status-effect`, `character-build`,
  `race-template`). Search/detail is optional per entity; the create/edit `contributions`
  module is the core. UI VMs are Hilt `@HiltViewModel`; repositories live in `repositories/`
  (`api` interface + `impl` `Default*Repository`, bound in `repositories/impl/.../RepositoryModule.kt`).
- **Persistence is one shared SQLDelight `CompendiumDatabase`** (all `.sq` files under
  `persistence/src/main/sqldelight/.../persistence/`). Each entity gets a thin
  `<Entity>Database.kt` wrapper exposing only its generated queries. Per-file drivers are
  provided in `app/.../di/DatabaseModule.kt` under `@Canonical` (compendium.db) and
  `@Contributions` (contributions.db) qualifiers.
- **Contributions-only entities** (`character-build`) have no canonical seed data, so their
  `Default*Repository` reads/writes the `@Contributions` DB only. Every stored row is
  therefore editable.
- **Canonical seed data** lives in `app/src/main/assets/*.csv`, parsed by per-entity loaders
  in `dataloader/` (bound in `app/.../di/DataLoaderModule.kt`) and lazily written into the
  `@Canonical` DB/cache on first read (`ensureCanonicalLoaded` in each `Default*Repository`).
  Canonical rows are read-only; saves may not create a new contribution under a canonical
  name. A contribution that nonetheless shares one (e.g. restored from an old backup) shadows
  the canonical row in the merged view and may still be updated in place.
- **Canonical races** seed from `races.csv` (`RaceName,Ability1..Ability6`, names only) into
  the `@Canonical` `RaceTemplateDatabase` as `canon:<name>` refs. The referenced racial
  abilities are first-class canonical ability listings seeded from `ability_listings.csv`
  (`name,type,description` rows with the description as the last, comma-tolerant column;
  unknown "???" details seed as `name,Racial ability,???` so the racial classification is
  kept — a bare `name` row is still parseable but leaves the listing untyped, which makes
  it a slot-picker candidate). Every ability name in `races.csv` must have a matching
  `ability_listings.csv` row or ref resolution drops it — guarded by
  `app/src/test/.../CanonicalRaceSeedDataTest.kt`. Outworlder (five per-individual "<Varies>"
  abilities) and Goblin (all six unknown) cannot satisfy the exactly-6 rule and are
  deliberately not seeded.
- **Cross-domain references are tagged-string soft refs** encoded by `domain/.../model/Refs.kt`
  `RefCodec`: `canon:<name>` for canonical, `contr:<id>` for a contributed ability listing,
  `mcontr:<id>`/`ccontr:<id>` for contributed manifestation/confluence essences. FK enforcement
  is OFF on the Android driver; Kotlin-side cascade (delete children before parents in one
  transaction) lives in each `<Entity>Database`. Do not add `REFERENCES` clauses — they were
  deliberately dropped project-wide at schema v7.

## Racial vs. non-racial abilities

- An ability is "racial" iff **all** its `Effect.AbilityEffect.type` are `AbilityType.RacialAbility`
  (`domain/.../model/AbilityType.kt`). The canonical filters are in
  `CharacterBuildContributionsViewModel`: `racialAbilityCandidates()` (effects all `RacialAbility`)
  and `slotAbilityCandidates()` (no `RacialAbility` effect). Reuse these predicates rather than
  inventing a new field. Race-template ability pickers source only racial candidates; essence/slot
  ability pickers exclude racial. Note essences never attach abilities in their own contributions
  UI — the only essence-facing ability picker is the character-build slot picker.

## SQLDelight schema changes (adding a table / migration)

Current `CompendiumDatabase.Schema.version` is **8**. To change the schema:
1. Edit/add the `.sq` file (fresh-install schema + queries).
2. Add `migrations/<oldVersion>.sqm` (e.g. `7.sqm` migrates v7→v8). Keep it additive where possible;
   `verifyMigrations` is on and will fail the build if migrations don't reproduce the `.sq` schema.
3. Regenerate the schema snapshot: `./gradlew :persistence:generateMainCompendiumDatabaseSchema`
   (writes `databases/<newVersion>.db`), then `./gradlew :persistence:verifyMainCompendiumDatabaseMigration`.
4. Update `MigrationTest.kt`: the `schema version is N` test, add a `migrate(..., from, to)` step to
   the incremental-ladder test, and bump `freshVNDriver` helpers. Do **not** add the new tables to
   `ALL_CURRENT_TABLES` unless every partial-migration test that calls `assertHasAllCurrentTables`
   also reaches the version that creates them.
- Gotcha: a single-column table's `SELECT *` maps each row to the **scalar** column type in
  SQLDelight, not a generated row class (see `RaceTemplateDatabase.readAllRaceTemplates`).

## Build / test

- `local.properties` with `sdk.dir` is required (not committed). Android SDK at `~/Android/Sdk`.
- Fast logic-layer loop (JVM/unit): `:domain:test :persistence:test :repositories:impl:testDebugUnitTest
  :<entity>:contributions:testDebugUnitTest`. These run `--offline`.
- Full `:app:assembleDebug` needs network the first time (aapt2 is not pre-cached for `--offline`).
- Repository unit tests can call `android.util.Log`; the module's test config returns default values,
  so log-on-drop paths are exercised without Robolectric.
