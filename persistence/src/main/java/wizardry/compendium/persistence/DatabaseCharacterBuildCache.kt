package wizardry.compendium.persistence

import wizardry.compendium.domain.model.CharacterBuild
import javax.inject.Inject

/**
 * Bridges [CharacterBuildCache] to [CharacterBuildDatabase].
 *
 * The read path cannot hydrate domain objects here (canonical refs live outside
 * persistence). Task 9 (DefaultCharacterBuildRepository) will bypass this cache
 * for reads and resolve raw rows directly. Until then, reads return an empty list
 * as a placeholder so :persistence:compileKotlin passes.
 *
 * The write path delegates to [CharacterBuildDatabase.writeAll] via [resolver].
 */
class DatabaseCharacterBuildCache @Inject constructor(
    private val database: CharacterBuildDatabase,
    private val resolver: BuildRefResolver,
) : CharacterBuildCache {
    private var cached: List<CharacterBuild>? = null

    override var contents: List<CharacterBuild>
        get() = cached ?: emptyList<CharacterBuild>().also { cached = it }
        set(value) {
            database.writeAll(value, resolver)
            cached = value
        }
}
