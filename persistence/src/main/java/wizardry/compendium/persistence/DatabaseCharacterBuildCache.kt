package wizardry.compendium.persistence

import wizardry.compendium.essences.model.CharacterBuild
import javax.inject.Inject

class DatabaseCharacterBuildCache @Inject constructor(
    private val database: CharacterBuildDatabase,
) : CharacterBuildCache {
    private var cached: List<CharacterBuild>? = null

    override var contents: List<CharacterBuild>
        get() = cached ?: database.readAll().also { cached = it }
        set(value) {
            database.writeAll(value)
            cached = value
        }
}
