# Canonical data pipeline

How canonical essence / awakening stone / ability listing / status effect data
gets from the source spreadsheet into the app at runtime, and how to regenerate
or extend it.

## Source

The canonical dataset originates with **llamawaffles555** (credited in the
README and About screen). It lives in a community-maintained Google Sheets
workbook on Discord — not in this repository. There is no programmatic export
job; the CSVs in `app/src/main/assets/` are hand-derived from that workbook.

When the spreadsheet gets a meaningful update, the workflow is:

1. Pull the latest columns from the workbook for the relevant tab.
2. Reshape into the narrow CSV format described below (columns and order
   matter — the loaders use positional `split(',')` parsing).
3. Drop the file into `app/src/main/assets/` overwriting the previous version.
4. Build and verify locally — the loaders are mostly position-tolerant but
   strict about referential integrity (see "Failure modes").

## On-disk layout

All canonical files live in `app/src/main/assets/` and are bundled into the
APK by the Android Gradle plugin. They are read at runtime via
`AssetManager.open(filename)` (see `AssetFileStreamSource`).

| File | Columns | Notes |
| --- | --- | --- |
| `essences.csv` | `name,rarity` | One row per **manifestation** essence. `rarity` is the `Rarity` enum name (`Common`, `Uncommon`, `Rare`, `Epic`, `Legendary`, `Unknown`). No header row. |
| `combinations.csv` | `essence1,essence2,essence3,confluenceName` | One row per known three-essence combination. The same `confluenceName` may appear on many rows; the loader merges them into a single `Essence.Confluence` whose `confluenceSets` lists every combination. All three referenced essences must exist in `essences.csv`. No header row. |
| `restricted.csv` | Two sections separated by the literal divider line `Always Restricted,,,` | **Top section** (above the divider): three-essence sets that are restricted as a *combination* (matched against `combinations.csv` rows by unordered set membership). Format `e1,e2,e3,confluenceName`. **Bottom section** (below the divider): individual essence or confluence names that are *always* restricted regardless of combination. Format `name,,,` — only the first column is read. |
| `awakening_stones.csv` | `name,rarity` | Same `Rarity` values as essences. No header row. Blank lines skipped. |
| `ability_listings.csv` | `name,...` | Only the first column is read; anything after the first comma is ignored, so the source CSV may carry extra metadata. Currently empty in canonical (the file exists but has zero rows). Failures are swallowed — the loader returns an empty list rather than throwing. |
| `status_effects.csv` | `name,type,stackable,description,properties` | **Has a header row** (the only file that does). `type` is a token like `Affliction.Curse` / `Boon.Holy`. `stackable` is `true`/`false` (case-insensitive). `properties` is **pipe-delimited** (`|`) so descriptions can contain commas. Currently header-only in canonical. |

### Why CSV with no header (mostly)?

Historical: the original loaders predate having a curated header convention.
Status effects added a header because its `description` column is free-form
and benefits from an explicit schema marker. New canonical tables should
include a header row and parse by column name — but don't churn the existing
files just for consistency.

## Code path

The dataloader is a JVM-only Gradle module (`dataloader/`, applies the
`compendium.jvm` convention plugin) that depends on `:essences` for the model
types. It deliberately has no Android dependency — `FileStreamSource` is the
seam.

```
                  ┌────────────────────────────┐
                  │ app/src/main/assets/*.csv  │
                  └──────────────┬─────────────┘
                                 │ AssetManager.open
                                 ▼
                  ┌────────────────────────────┐
   FileStreamSource ◀── bound by ──┤ AssetFileStreamSource (:app)│
                  └──────────────┬─────────────┘
                                 │ getInputStreamFor
                                 ▼
              ┌──────────────────────────────────────┐
              │  EssenceCsvLoader                    │
              │  AwakeningStoneCsvLoader             │
              │  AbilityListingCsvLoader             │
              │  StatusEffectCsvLoader               │
              └──────────────┬───────────────────────┘
                             │ implements
                             ▼
              ┌──────────────────────────────────────┐
              │  EssenceDataLoader (interface)       │
              │  AwakeningStoneDataLoader            │
              │  AbilityListingDataLoader            │
              │  StatusEffectDataLoader              │
              └──────────────┬───────────────────────┘
                             │ injected into
                             ▼
              ┌──────────────────────────────────────┐
              │  Default*Repository (:app)           │
              │  - calls loader on first access      │
              │  - memoizes into @Canonical *Cache   │
              │  - merges with @Contributions cache  │
              └──────────────────────────────────────┘
```

DI wiring lives in `app/src/main/java/wizardry/compendium/di/DataLoaderModule.kt`:
each `*CsvLoader` is `@Binds`-bound to its interface as a `@Singleton`, and
`AssetFileStreamSource` is bound to `FileStreamSource`. The CSV loader is the
*only* implementation that ships, but anything implementing the interface
(e.g. a fake in tests) plugs in cleanly.

### Lazy load + cache

The repositories don't load on startup. The first call to
`get*()` / observation of the corresponding flow triggers
`dataLoader.load*Data()` on `Dispatchers.IO`; the result is stored in the
`@Canonical` `*Cache` and reused thereafter. The user-contributions cache
(`@Contributions`) is a separate SQLite database (see
[Dual-database contributions feature](contributions-import-export.md) and
[contribution conflicts](contributions-conflicts.md)).

### Sort order

Every loader returns its list `sortedBy { it.name }`. Search screens rely on
that ordering being stable; don't change it without auditing call sites.

## Essence / confluence assembly (the only non-trivial loader)

`EssenceCsvLoader` does three reads and two joins:

1. **Restricted-name set** from `restricted.csv` (lines *after* the
   `Always Restricted` divider, first column only).
2. **Restricted-combination set** from `restricted.csv` (lines *before* the
   divider, parsed as `setOf(first, second, third)`).
3. **Base manifestations** from `essences.csv`. Each row becomes an
   `Essence.Manifestation` with `description = "none"` (canonical descriptions
   live in the per-confluence books — manifestations don't have curated text
   yet) and `restricted` derived from the name set.
4. **Confluences** from `combinations.csv`. For each row it looks up the
   three referenced manifestations (by name) from step 3 and constructs a
   `ConfluenceSet`, marking the set restricted if it appears in the
   restricted-combination set. Multiple rows with the same `confluenceName`
   are merged: the `merge` extension function unions `confluenceSets` so a
   single confluence ends up with every known combination that produces it.
5. The final list is `(manifestations + confluences).sortedBy { name }`.

## Failure modes

| Loader | On bad input | Why |
| --- | --- | --- |
| `EssenceCsvLoader` | **Throws** (NPE on unknown manifestation lookup, `IllegalArgumentException` on malformed row destructuring). | Referential integrity matters — a confluence referencing a missing manifestation is a data bug we want to catch at startup, not silently drop. |
| `AwakeningStoneCsvLoader` | Throws on malformed row. Skips blank lines. | Same reasoning. |
| `AbilityListingCsvLoader` | **Swallows everything** — `runCatching { ... }.getOrDefault(emptyList())`. | Listings are nice-to-have; if the file is missing or mangled the rest of the app should still load. |
| `StatusEffectCsvLoader` | Throws on malformed row, unknown `StatusType` token, or unknown `Property` token. Returns empty list when the file has only a header. | Strict because the type/property tokens are typed enums — a typo is a data bug. The header-only case is the canonical empty state. |

When you add new canonical rows, run the app once on device or emulator —
the first essence/awakening stone search query will exercise the loader and
surface any data bugs immediately.

## Adding a new canonical table

If you add a new domain that needs canonical data:

1. Create the CSV under `app/src/main/assets/`, with a header row and
   pipe-delimited multi-value columns where needed.
2. Add an interface `XDataLoader` in `dataloader/.../essences/dataloader/`
   exposing `suspend fun loadXData(): List<X>`.
3. Add a `XCsvLoader @Inject constructor(source: FileStreamSource)` that
   implements it and uses `withContext(Dispatchers.IO)` for the read.
4. Bind it as `@Singleton` in `DataLoaderModule`.
5. Inject the interface into your repository, gate it behind an
   `@Canonical XCache`, and follow the load-once-on-first-access pattern from
   `DefaultEssenceRepository.ensureCanonicalLoaded`.
6. If the domain accepts user contributions, also follow the
   [evolving-schema](../README.md) playbook for the `:persistence` side and
   the dual-database contributions feature notes.

## What this pipeline is *not*

- **Not a build-time generator.** Nothing parses these CSVs at compile time.
  No code is generated from them. Adding a new column does not require a
  Gradle change.
- **Not versioned.** The app has no "canonical data version" string today;
  surfacing one in the About screen is tracked in `TODO.md`.
- **Not networked.** Canonical data is fully bundled. There is no remote
  fetch, no update-on-launch path, and no migration story for canonical CSV
  changes — every update ships in an APK release.
