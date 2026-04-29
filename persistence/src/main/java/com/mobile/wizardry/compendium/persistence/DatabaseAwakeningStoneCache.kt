package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.AwakeningStone
import javax.inject.Inject

class DatabaseAwakeningStoneCache @Inject constructor(
    private val database: AwakeningStoneDatabase,
) : AwakeningStoneCache {
    private var cached: List<AwakeningStone>? = null

    override var contents: List<AwakeningStone>
        get() = cached ?: database.readAll().also { cached = it }
        set(value) {
            database.writeAll(value)
            cached = value
        }
}
