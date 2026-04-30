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
- **Randomizer** — roll randomized essence/confluence combinations.
- **Contributions** — add or edit user-supplied data for essences, abilities, and awakening stones, kept separate from the canonical dataset so canonical updates don't clobber your additions.

## Tech

- Kotlin, Jetpack Compose, Material 3
- Hilt for DI, Navigation Compose for screens
- SQLDelight over two SQLite databases (canonical + contributions), with a DataStore-backed toggle for which one is in use
- Multi-module Gradle build (one module per feature; shared `model-core`, `persistence`, `design`)

## Building

```bash
./gradlew :app:assembleDebug
```

Requires JDK 21 and the Android SDK. `local.properties` should point `sdk.dir` at your Android SDK install.

## License

The source code in this repository is the author's own. The underlying setting, characters, and magical system belong to Shirtaloon — this project distributes data describing that system for fan reference only.
