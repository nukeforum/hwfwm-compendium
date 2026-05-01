# Contribution import/export

Wire-format spec, transport, versioning, conflict handling, and the per-domain
checklist for adding new contribution types to the format.

## Goals

- Compact enough for plain-text Discord shares of typical contribution bundles.
- Self-contained: a paste/file can be imported by any installation of the app
  with no out-of-band coordination.
- Forward-compatible: schema changes ship with migrations, so an old paste
  imports cleanly into a new build.
- Stable: enum index assignments never change, enforced by a build-time lint.

## Granularity

Three import/export entry points, all sharing the same wire format:

| Surface | Action | Scope |
| --- | --- | --- |
| Detail screen (essence / awakening stone / ability listing) | Export | One entity (the one being viewed) |
| Contribute screen | Import | Paste a one-entity blob; pre-fills the form |
| Settings screen | Import / Export | Per-domain table or full database |

## Transport

### Wire form

Both the **share** path (Android `ACTION_SEND` plain text) and the **export**
path (file) carry the same byte string:

```
gzip(json) → base64
```

- JSON serialization uses `kotlinx.serialization.json` with
  `encodeDefaults = false` and `ignoreUnknownKeys = true`.
- `Gzip` compresses the JSON.
- `Base64` (no wrapping, RFC 4648 standard) makes it text-safe.

The result is always one self-contained string. Importer:
- Strips whitespace,
- base64-decodes,
- gunzips,
- parses JSON,
- runs the version migrator chain,
- decodes into model objects.

### Share size ceiling

`ShareSizeLimitBytes = 100 * 1024` (100 KB of base64 string). Above this, the
share button surfaces "Bundle too large to share — export to a file instead."
Reasoning: Android's binder soft cap is around 1 MB total IPC, but practical
text-share targets (Discord, IMEs, clipboard apps) clip well below that.
100 KB is a safe ceiling for `ACTION_SEND` `EXTRA_TEXT` across the realistic
universe of receivers.

The export-to-file path has no ceiling.

### File extension and MIME

- Extension: `.compendium`
- MIME: `text/plain`

A `.compendium` file is literally the share string written to disk. Any text
editor can open it; the importer accepts both file picker and pasted text
through the same code path.

## Wire format v1

### Envelope

```json
{
  "v": 1,
  "e": [ /* manifestations */ ],
  "c": [ /* confluences */ ],
  "s": [ /* awakening stones */ ],
  "a": [ /* ability listings */ ]
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `v` | Int | Wire format version. Required. |
| `e` | List | Essence manifestations. Omit when empty. |
| `c` | List | Essence confluences. Omit when empty. |
| `s` | List | Awakening stones. Omit when empty. |
| `a` | List | Ability listings. Omit when empty. |

### Manifestation

```json
{ "n": "Wind", "k": 1, "r": 0, "p": [], "d": "...", "x": false }
```

| Field | Type | Notes |
| --- | --- | --- |
| `n` | String | Name. Required. |
| `k` | Int | Rank index. Required. |
| `r` | Int | Rarity index. Required. |
| `p` | List<Int> | Property indices. Omit when empty. |
| `d` | String | Description. Omit when empty. |
| `x` | Boolean | `isRestricted`. Omit when `false`. |

### Confluence

```json
{ "n": "Doom", "s": [["Sin","Blood","Dark",0]], "x": false }
```

| Field | Type | Notes |
| --- | --- | --- |
| `n` | String | Name. Required. |
| `s` | List<ConfluenceSet> | Combination sets. Required, non-empty. |
| `x` | Boolean | `isRestricted`. Omit when `false`. |

A `ConfluenceSet` is a 4-element tuple: `[String, String, String, Int]` →
`[manifestation1, manifestation2, manifestation3, restrictedFlag]`. The
restricted flag is `0` (not restricted) or `1` (restricted). Tuples are used
instead of objects to save ~25 chars per set; the shape is fixed and unlikely
to change.

Manifestations are referenced **by name only**. The importer resolves names
against canonical + already-imported contributions; an unresolved name fails
the import for that confluence (other entries in the same envelope still
import).

### Awakening stone

```json
{ "n": "Volcano", "r": 3 }
```

| Field | Type | Notes |
| --- | --- | --- |
| `n` | String | Name. Required. |
| `r` | Int | Rarity index. Required. |

The contribution form only captures name + rarity today. Wire form mirrors
that. If/when the form captures additional fields, they are added in a new
wire-format version with a migration.

### Ability listing

```json
{ "n": "Frost Bolt", "f": [ /* effects */ ] }
```

| Field | Type | Notes |
| --- | --- | --- |
| `n` | String | Name. Required. |
| `f` | List<Effect> | Effects. Required, non-empty. |

### Effect

```json
{ "k": 1, "t": 3, "p": [19,26], "c": [["U",2,0]], "d": "...", "o": "30s", "q": "" }
```

| Field | Type | Notes |
| --- | --- | --- |
| `k` | Int | Rank index. Required. |
| `t` | Int | Ability type index. Required. |
| `p` | List<Int> | Property indices. Omit when empty. |
| `c` | List<Cost> | Costs. Omit when empty. |
| `d` | String | Description. Required. |
| `o` | String | Cooldown (e.g. `"30s"`, `"5min"`). Omit when empty. |
| `q` | String | Replacement key. Omit when empty. |

A `Cost` is a 3-element tuple: `[String, Int, Int]` →
`[kind, amountIndex, resourceIndex]` where `kind` is `"U"` (Upfront) or `"O"`
(Ongoing). `Cost.None` is never serialized — empty cost list at runtime is
encoded as omitted `c`, and the receiver materializes `[Cost.None]` on decode
the same way the contribute-form draft does.

## Enum index tables

These mappings are part of the wire contract. Adding values is fine; the new
value gets the next-highest index. Removing or reordering values is a
breaking change and requires a schema-version bump with a migrator.

### Rarity (`r`)

| Index | Value |
| --- | --- |
| 0 | Common |
| 1 | Uncommon |
| 2 | Rare |
| 3 | Epic |
| 4 | Legendary |
| 5 | Unknown |

### Rank (`k`)

| Index | Value |
| --- | --- |
| 0 | Unranked |
| 1 | Iron |
| 2 | Bronze |
| 3 | Silver |
| 4 | Gold |
| 5 | Diamond |
| 6 | Transcendent |

### AbilityType (`t`)

| Index | Value |
| --- | --- |
| 0 | SpecialAttack |
| 1 | SpecialAbility |
| 2 | RacialAbility |
| 3 | Spell |
| 4 | Aura |
| 5 | Conjuration |
| 6 | Familiar |
| 7 | Summoning |
| 8 | Use |

### Resource (cost tuple element 2)

| Index | Value |
| --- | --- |
| 0 | Mana |
| 1 | Stamina |
| 2 | Health |

### Amount (cost tuple element 1)

| Index | Value |
| --- | --- |
| 0 | None |
| 1 | VeryLow |
| 2 | Low |
| 3 | Moderate |
| 4 | High |
| 5 | VeryHigh |
| 6 | Extreme |
| 7 | BeyondExtreme |

### Property (`p`)

Properties are a `sealed interface` with `object` subtypes. The index is the
declaration order in `Property.kt`.

| Index | Value | Index | Value |
| --- | --- | --- | --- |
| 0 | Affliction | 25 | Light |
| 1 | Blood | 26 | Lightning |
| 2 | Boon | 27 | Magic |
| 3 | Channel | 28 | ManaOverTime |
| 4 | Cleanse | 29 | Melee |
| 5 | Combination | 30 | Momentum |
| 6 | Conjuration | 31 | Movement |
| 7 | Consumable | 32 | Nature |
| 8 | CounterExecute | 33 | Perception |
| 9 | Curse | 34 | Poison |
| 10 | DamageOverTime | 35 | Recovery |
| 11 | Dark | 36 | Restoration |
| 12 | Darkness | 37 | Retributive |
| 13 | Dimension | 38 | Ritual |
| 14 | Disease | 39 | Sacrifice |
| 15 | Drain | 40 | ShapeChange |
| 16 | Elemental | 41 | Signal |
| 17 | Essence | 42 | Stacking |
| 18 | Execute | 43 | StaminaOverTime |
| 19 | Fire | 44 | Summon |
| 20 | HealOverTime | 45 | Teleport |
| 21 | Healing | 46 | Tracking |
| 22 | Holy | 47 | Trap |
| 23 | Ice | 48 | Unholy |
| 24 | Illusion | 49 | Vehicle |
|    |        | 50 | Wounding |
|    |        | 51 | Zone |

## Versioning and migrations

The envelope's `v` field is the only version. It increments by 1 on any
breaking change (rename of a wire field, change to an enum index, new required
field, change to tuple shape). Adding optional fields with omit-on-default
behavior is **not** a breaking change.

### Migrator chain

Each version increment ships with a `WireMigrator` that converts the previous
version's envelope to the current one, operating on `JsonElement`. Imports
flow: `parse → loop migrators while v < CURRENT → decode`.

```kotlin
interface WireMigrator {
    val from: Int
    val to: Int
    fun migrate(envelope: JsonObject): JsonObject
}
```

A registry holds all migrators. Importer rejects envelopes with `v > CURRENT`
("export from a newer build, please update").

### Adding a new version

1. Write the new wire types under `wire/v<N>/` (or the equivalent package).
2. Write a `WireMigrator` from the prior version to the new one.
3. Register it.
4. Bump the `CURRENT_WIRE_VERSION` constant.
5. Add tests that round-trip a v(N-1) blob through the migrator into a v(N)
   model object.

Old test fixtures from prior versions stay around as regression coverage —
they should always import cleanly through the chain.

## Conflict handling at import

Each import returns per-entry results:

```kotlin
sealed interface ImportResult {
    data class Added(val name: String) : ImportResult
    data class SkippedDuplicate(val name: String) : ImportResult
    data class Failed(val name: String, val reason: String) : ImportResult
}
```

When an imported entry would collide with an existing one (canonical or
contribution), it is reported as `SkippedDuplicate` rather than written. The
user resolves these the same way they resolve any other conflict: through the
existing conflicts UI, which fires when canonical/contributions disagree.

Specifically:
- Importing a contribution whose name matches an existing **contribution**:
  skip with a SkippedDuplicate. (User can edit the existing one if they want
  the imported version's content.)
- Importing a contribution whose name matches a **canonical** entry: skip with
  a SkippedDuplicate. (No conflict surfaces, because we never wrote it.)
- Importing a confluence whose combination is owned by a canonical confluence
  of a different name: skip with a SkippedDuplicate.

Imports never silently overwrite. The user reviews the import-result summary
post-import.

## Plugin-driven schema lock

Wire types are described once via annotations (`@WireFormat`, `@WireField`,
`@WireType`, `@WireEnum`) defined in the `:wire-annotations` module. A KSP
processor in `:wire-ksp` walks the annotated graph at compile time and emits
a JSON snapshot of the wire shape — fields with their aliases, defaults, and
Kotlin types; enum types with their ordered entries — to
`build/generated/ksp/main/resources/wire-schemas/v<N>.json`.

The committed lock lives at `wire/wire-schemas/v<N>.json`. Two Gradle tasks
in `:wire`:

- `:wire:checkWireSchemaLock` (run as part of `check`) — fails the build if
  the generated snapshot differs from the committed lock. Adds, removes,
  renames, and enum-order changes all surface as drift.
- `:wire:updateWireSchemaLock` — overwrites the committed lock with the
  generated snapshot. Run only when you have made an intentional schema
  change, and commit the updated lock alongside the change (and migrator,
  if the version bumped).

### What's in the snapshot

Each wire type entry records:
- FQN of the Kotlin class
- Optional `@WireType(alias = ...)` value
- Each field's name, alias, previous-aliases, Kotlin type (with generic
  arguments rendered), `omitOnDefault` setting, and whether the field has a
  Kotlin-declared default

Each enum entry records the FQN and the ordered list of entries (for
`enum class`es) or sealed-subtype simple names (for `sealed interface`s).

### Backwards-compatible vs breaking changes

Mechanical / non-breaking (no version bump needed):
- Adding a new wire type (no migrator needed; old envelopes don't reference it)
- Adding an optional field with a default (old envelopes decode the default)
- Adding an enum entry at the **end** of the declaration list

Breaking (requires version bump + migrator):
- Renaming a field — set `previousAlias = "..."` on the renamed field; the
  generated migrator (Tier 2) reads the prior alias and writes the current
  one. No version bump strictly required if the rename is captured this way.
- Removing a field — the old envelope's value is dropped; safe but lossy.
- Reordering or removing enum entries — needs a hand-written migrator and a
  version bump.
- Changing a field's Kotlin type in any way — needs a hand-written migrator.

The diff engine (Tier 2) classifies changes and either auto-generates a
migrator or fails the build with a stub for the developer to fill in.

## Per-domain checklist for adding a new contribution type

When you add a new contribution domain (call it `X`):

1. **Wire types**: define the v<CURRENT> shape in the wire package. Single-
   letter aliased fields, `encodeDefaults = false`, omit-on-default for any
   field with a sensible default.
2. **Enum index tables**: extend the relevant tables in this doc, or add new
   ones if the domain introduces enums. Add the enum classes to the lint lock.
3. **Encoder/decoder**: implement `WireEncoder` and `WireDecoder` for the
   domain. Encoders accept the user-form draft shape; decoders return it.
4. **Envelope**: add a domain-letter field to the `Envelope` data class
   (e.g., `"x"`). Wire it through the encode/decode pipeline.
5. **Conflict resolution**: import returns `ImportResult` per entry. Reuse the
   detection logic from `:essences/Conflict.kt` to decide
   Added/SkippedDuplicate. If your domain has a domain-specific shape of
   conflict (like essence's combination collision), surface it from the
   importer the same way.
6. **Surfaces**:
   - Detail screen → "Export" action that calls the per-domain encoder.
   - Contribute screen → "Import" action that opens a paste/file dialog,
     decodes a one-entry envelope, prefills the form.
   - Settings screen → add the domain to the per-table dropdown for export and
     to the import target list.
7. **Migrations**: if this is a wire-version bump, write the migrator. If you
   are extending an existing version with optional fields, no migrator needed.
8. **Lint lock**: re-run `./gradlew updateWireSchemaLock` and commit the new
   hashes.
9. **Tests**:
   - Round-trip: contribution → encode → decode → contribution.
   - Versioned regression: import every committed test fixture from prior
     versions; assert success.
   - Migrator-chain: hand-craft a v(N-1) blob, run migration, assert v(N)
     output equality.
   - Conflict at import: contribution that matches canonical → SkippedDuplicate;
     contribution that matches an existing contribution → SkippedDuplicate.

## Known limitations / not in scope for v1

- **Build sharing** (compact reference-only export of a curated entity
  selection from canonical + contributions) is a future feature with its own
  format. Not addressed here.
- **Ability listings always export with full effect descriptions.** A bundle
  large enough to exceed the 100 KB share ceiling falls back to file export.
- **Round-trip of a confluence whose member manifestations are user
  contributions but were not included in the same envelope** fails for the
  receiver. Encoder warns the exporter; receiver gets a clear error.
