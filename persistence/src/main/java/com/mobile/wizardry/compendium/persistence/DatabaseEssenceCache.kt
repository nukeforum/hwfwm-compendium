package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.Essence
import javax.inject.Inject

class DatabaseEssenceCache @Inject constructor(
    private val database: EssenceDatabase,
) : EssenceCache {
    private var cached: List<Essence>? = null

    override var contents: List<Essence>
        get() = cached ?: database.readAll().also { cached = it }
        set(value) {
            database.writeAll(value)
            cached = value
        }
}
