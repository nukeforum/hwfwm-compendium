package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface EssenceContributionsToggleFlow {
    val essenceContributionsEnabled: Flow<Boolean>
}
