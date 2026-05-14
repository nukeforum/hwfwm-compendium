package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface EssencesAsAwakeningStonesToggleFlow {
    val essencesAsAwakeningStonesEnabled: Flow<Boolean>
}
