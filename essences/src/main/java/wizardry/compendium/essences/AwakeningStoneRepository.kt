package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneRepository {
    val awakeningStones: Flow<List<AwakeningStone>>

    suspend fun getAwakeningStones(): List<AwakeningStone>

    suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult

    suspend fun isContribution(name: String): Boolean

    suspend fun deleteContribution(name: String): ContributionResult

    suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult
}
