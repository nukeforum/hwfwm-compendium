package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity
import javax.inject.Inject

class AwakeningStoneDatabase @Inject constructor(driver: SqlDriver) : AwakeningStoneCache {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.awakeningStonesQueries

    override val identified: List<IdentifiedAwakeningStone>
        get() = q.selectAllAwakeningStones().executeAsList().map { row ->
            IdentifiedAwakeningStone(
                id = row.id,
                stone = AwakeningStone.of(
                    name = row.name,
                    rarity = Rarity.valueOf(row.rarity),
                ),
            )
        }.sortedBy { it.stone.name }

    override fun insert(stone: AwakeningStone): Long = db.transactionWithResult {
        q.insertAwakeningStone(name = stone.name, rarity = stone.rarity.name)
        q.lastInsertRowId().executeAsOne()
    }

    override fun update(id: Long, stone: AwakeningStone) {
        q.updateAwakeningStoneFully(name = stone.name, rarity = stone.rarity.name, id = id)
    }

    override fun deleteById(id: Long) {
        q.deleteAwakeningStoneById(id = id)
    }

    override fun findIdByName(name: String): Long? =
        q.selectAwakeningStoneId(name = name).executeAsOneOrNull()

    override fun replaceAll(stones: List<AwakeningStone>) = db.transaction {
        q.deleteAllAwakeningStones()
        stones.forEach { stone ->
            q.insertAwakeningStone(name = stone.name, rarity = stone.rarity.name)
        }
    }
}
