package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface EssencesAsAwakeningStonesToggleFlow {
    val essencesAsAwakeningStonesEnabled: Flow<Boolean>
}
