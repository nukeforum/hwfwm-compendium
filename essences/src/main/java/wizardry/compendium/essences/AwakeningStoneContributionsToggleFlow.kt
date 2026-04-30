package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface AwakeningStoneContributionsToggleFlow {
    val awakeningStoneContributionsEnabled: Flow<Boolean>
}
