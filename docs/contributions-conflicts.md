# Contribution conflict handling

This describes how the Compendium app detects and resolves conflicts between
user-supplied contributions and the canonical dataset, and the per-domain
checklist for adding the same machinery to a new contribution type.

## Problem

User contributions live in a separate database (`Contributions`) from the
canonical dataset (`Canonical`). Save-time validation prevents the user from
creating a contribution whose name (or, for confluences, combination) already
exists in canonical. But canonical changes between releases — so a contribution
that was valid yesterday can collide with the latest canonical data after an
APK update.

A *conflict* is any case where merging contributions on top of canonical would
shadow or contradict canonical content.

## Conflict types

| Domain | Type | Trigger |
| --- | --- | --- |
| Essence | `NameCollision` | A contributed essence (manifestation or confluence) has the same normalized name as a canonical essence. |
| Essence | `CombinationCollision` | A contributed `Essence.Confluence` has a `confluenceSet` whose `{essence, essence, essence}` combination is owned by a *different* canonical confluence. |
| Awakening Stone | `NameCollision` | A contributed stone has the same normalized name as a canonical stone. |
| Ability Listing | `NameCollision` | A contributed listing has the same normalized name as a canonical listing. |

Notes:
- Names are compared with `trim().lowercase()`.
- For essences, a same-name *and* same-combination pair is reported as a
  `NameCollision` only — not also as a combination collision. The contribution
  is redundant rather than contradictory.

## Behavioral guarantees

When a domain has any conflicts:

1. **The merge is gated.** `getEssences()` / `getAwakeningStones()` /
   `getAbilityListings()` return canonical only, even if the user's toggle is
   on. The persisted toggle is *not* mutated; once conflicts clear, the user's
   stored preference takes effect again.
2. **The toggle is locked off in the UI.** The settings screen disables that
   domain's switch with a "Resolve N conflicts to enable" hint.
3. **The hazard icon appears in the top app bar.** Tapping it routes to the
   conflicts list.

Conflicts in one domain do not gate the other domains — essence conflicts only
lock the essence toggle.

## Detection lifecycle

Conflicts are *derived state*. They are not persisted. Each repository exposes
both:

```kotlin
val conflicts: Flow<List<XConflict>>
suspend fun getConflicts(): List<XConflict>
```

The flow is recomputed any time canonical or contributions change. The pure
detection functions live in `:essences`:

- `detectEssenceConflicts(canonical, contributions)`
- `detectAwakeningStoneConflicts(canonical, contributions)`
- `detectAbilityListingConflicts(canonical, contributions)`

These are the source of truth for "is this combination of canonical and
contributions in conflict." Tests live in
`essences/src/test/.../ConflictDetectionTest.kt`.

## Resolution

The conflicts screen lists every unresolved conflict, grouped by domain. Each
row opens a resolution dialog with these actions:

- **Edit Contribution** — navigates to the existing edit screen for that
  contribution. The user can rename or alter combinations to clear the conflict.
- **Delete Contribution** — removes the contribution.
- **Remove This Combination** *(combination conflicts only)* — removes just the
  offending `confluenceSet`. If it was the only combination, the whole
  contribution is deleted.

There is no "ignore" or "snooze". Resolution is the only path forward.

## Adding a new contribution type

When you introduce a new contribution domain, follow this checklist:

1. **Define a `Conflict` sealed type** in `:essences/Conflict.kt`. It must
   implement `Conflict` (so it has `title`, `summary`, `key`). Add at minimum a
   `NameCollision` variant. Add domain-specific variants where they apply
   (e.g., `CombinationCollision` for essences).
2. **Add a `detectXConflicts(canonical, contributions): List<XConflict>` pure
   function** alongside the type.
3. **Extend the repository interface** to expose:
   ```kotlin
   val conflicts: Flow<List<XConflict>>
   suspend fun getConflicts(): List<XConflict>
   ```
4. **Implement in the `Default*Repository`**:
   - Wire `conflicts` as `combine(toggleFlow, invalidations) { _, _ -> getConflicts() }`.
   - Gate the read path: `getX()` must return canonical when conflicts are
     present, regardless of the toggle.
5. **Wire into `ConflictsViewModel`**: inject the new repo, fold its conflicts
   into `ConflictsState`, and add domain-specific resolution methods (delete,
   any partial-resolution variants).
6. **Render in `ConflictsScreen`**: add a group header + items for the new
   domain; route the resolution dialog's actions to the new VM methods.
7. **Settings UI**: add a `conflictCount` flow on `SettingsViewModel` for the
   new domain, and a `ToggleRow` with `conflictCount` in `SettingsScreen`.
8. **Top bar badge**: nothing to do — `ConflictsBadge` already reads the
   aggregated `state.total`.
9. **Tests**:
   - Pure detection tests in `essences/src/test`.
   - Read-path gating + delete tests in `app/src/test/.../Default*RepositoryConflictTest.kt`.
   - Aggregation + resolution tests in `conflicts/src/test/.../ConflictsViewModelTest.kt`.

## Known limitations

- The canonical cache is populated on first load and never re-loaded at runtime.
  In practice this means a freshly-installed APK with new canonical data will
  pick up the changes (because the canonical SQLite table is empty), but a
  hot-reload of canonical mid-session won't. The conflict-handling code reacts
  whenever canonical actually changes, so when canonical re-sync / version
  surfacing lands (see project TODO), conflicts will start firing in real
  user sessions.
- Conflicts use exact normalized-name comparison. There is no fuzzy matching
  (e.g., "Wind " vs "Whind") — those are treated as distinct entities and would
  not be reported as conflicts.
