package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface EssenceContributionsToggleFlow {
    val essenceContributionsEnabled: Flow<Boolean>
}
