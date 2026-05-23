package wizardry.compendium.persistence

import wizardry.compendium.domain.model.AwakeningStone

data class IdentifiedAwakeningStone(val id: Long, val stone: AwakeningStone)

interface AwakeningStoneCache {
    val identified: List<IdentifiedAwakeningStone>

    val contents: List<AwakeningStone>
        get() = identified.map { it.stone }

    fun insert(stone: AwakeningStone): Long
    fun update(id: Long, stone: AwakeningStone)
    fun deleteById(id: Long)
    fun findIdByName(name: String): Long?
    fun replaceAll(stones: List<AwakeningStone>)
}
