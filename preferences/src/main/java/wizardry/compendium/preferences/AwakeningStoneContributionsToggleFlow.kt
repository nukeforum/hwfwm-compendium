package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface AwakeningStoneContributionsToggleFlow {
    val awakeningStoneContributionsEnabled: Flow<Boolean>
}
