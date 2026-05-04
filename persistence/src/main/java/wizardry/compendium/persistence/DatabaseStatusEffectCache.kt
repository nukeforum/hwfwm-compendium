package wizardry.compendium.persistence

import wizardry.compendium.essences.model.StatusEffect
import javax.inject.Inject

class DatabaseStatusEffectCache @Inject constructor(
    private val database: StatusEffectDatabase,
) : StatusEffectCache {
    private var cached: List<StatusEffect>? = null

    override var contents: List<StatusEffect>
        get() = cached ?: database.readAll().also { cached = it }
        set(value) {
            database.writeAll(value)
            cached = value
        }
}
