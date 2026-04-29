package com.mobile.wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import com.mobile.wizardry.compendium.essences.model.AwakeningStone
import com.mobile.wizardry.compendium.essences.model.Rarity
import javax.inject.Inject

class AwakeningStoneDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)

    fun writeAll(stones: List<AwakeningStone>) {
        db.transaction {
            db.awakeningStonesQueries.deleteAllAwakeningStones()
            stones.forEach { stone ->
                db.awakeningStonesQueries.insertAwakeningStone(
                    name = stone.name,
                    rarity = stone.rarity.name,
                )
            }
        }
    }

    fun readAll(): List<AwakeningStone> {
        return db.awakeningStonesQueries.selectAllAwakeningStones().executeAsList()
            .map { row ->
                AwakeningStone.of(
                    name = row.name,
                    rarity = Rarity.valueOf(row.rarity),
                )
            }
            .sortedBy { it.name }
    }
}
