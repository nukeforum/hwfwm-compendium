# HWFWM Compendium

An Android reference app for the magical system from the *He Who Fights With Monsters* book series. Browse essences and their abilities, look up awakening stones, roll randomized essence sets, and contribute corrections or additions when the canonical data falls short.

This is an unofficial fan project. It is not affiliated with, endorsed by, or licensed by the author or publisher.

## Credits

- **Shirtaloon (Travis Deverell)** — author of *He Who Fights With Monsters*. The magical system, essences, abilities, awakening stones, and all related lore are his creation. Go read the books.
- **llamawaffles555** (Discord) — aggregated and maintained the spreadsheet of essences, abilities, and awakening stones that seeds this app's canonical data.

## Features

- **Essence search & details** — browse the canonical list of essences and the abilities they grant.
- **Ability search & details** — find abilities by name, type, or effect.
- **Awakening stone search & details** — look up awakening stones and the abilities they confer.
- **Status effect search & details** — look up status effects, including ones referenced by abilities.
- **Character build search & details** — browse builds composed of an essence loadout plus chosen abilities.
- **Randomizer** — roll randomized essence/confluence combinations.
- **Contributions** — add or edit user-supplied entries for essences, abilities, awakening stones, status effects, and character builds, kept in a separate database so canonical updates don't clobber your additions. Renaming a contributed entity cascades through every other contribution that referenced the old name.
- **Conflicts screen** — surfaces collisions and dangling references across your contributions, walking every cross-entity reference and embedded status token.
- **Share / Export** — share a plain-text summary of any entity, or export the Wire-format bundle for re-import in another install.
- **Google Drive backup** — opt-in OAuth-backed periodic backup and restore of the contributions database and preferences via the user's own Drive app-folder.

## Tech

- Kotlin, Jetpack Compose, Material 3
- Hilt for DI, Navigation Compose for screens
- SQLDelight over two SQLite databases (canonical + contributions), with a DataStore-backed toggle for which one is in use
- Multi-module Gradle build (one module per feature; shared `model-core`, `persistence`, `design`, `wire`)
- KSP-generated wire codec for import/export and Drive backup
- WorkManager-driven `:drive-backup` module using Credential Manager + Drive REST v3

## Building

```bash
./gradlew :app:assembleDebug
```

Requires JDK 21 and the Android SDK. `local.properties` should point `sdk.dir` at your Android SDK install.

## License

The source code in this repository is released under the [MIT License](LICENSE).

The underlying setting, characters, and magical system belong to Shirtaloon — this project distributes data describing that system for fan reference only. The MIT License covers the code and the compilation of data in this repository, not the underlying *He Who Fights With Monsters* intellectual property.
