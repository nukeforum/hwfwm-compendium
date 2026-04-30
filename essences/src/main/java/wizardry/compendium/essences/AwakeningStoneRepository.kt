package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.AwakeningStone

interface AwakeningStoneRepository {
    val awakeningStones: Flow<List<AwakeningStone>>

    suspend fun getAwakeningStones(): List<AwakeningStone>

    suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult
}
