package wizardry.compendium.persistence

import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

class DatabaseAbilityListingCache @Inject constructor(
    private val database: AbilityListingDatabase,
) : AbilityListingCache {
    private var cached: List<Ability.Listing>? = null

    override var contents: List<Ability.Listing>
        get() = cached ?: database.readAll().also { cached = it }
        set(value) {
            database.writeAll(value)
            cached = value
        }
}
